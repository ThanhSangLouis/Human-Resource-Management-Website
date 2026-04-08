package org.example.hrmsystem.service;

import org.example.hrmsystem.dto.NotificationResponse;
import org.example.hrmsystem.exception.ResourceNotFoundException;
import org.example.hrmsystem.model.Notification;
import org.example.hrmsystem.model.NotificationDeliveryStatus;
import org.example.hrmsystem.model.NotificationType;
import org.example.hrmsystem.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final TaskExecutor notificationTaskExecutor;
    private final PlatformTransactionManager transactionManager;
    private TransactionTemplate requiresNewTemplate;

    @Value("${app.mail.from:noreply@hrm.local}")
    private String mailFrom;

    /** Khi false: không gửi SMTP thật, đánh dấu SENT để demo UI (khớp application.properties). */
    @Value("${app.mail.send-enabled:true}")
    private boolean mailSendEnabled;

    public NotificationService(
            NotificationRepository notificationRepository,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Qualifier("notificationTaskExecutor") TaskExecutor notificationTaskExecutor,
            PlatformTransactionManager transactionManager
    ) {
        this.notificationRepository = notificationRepository;
        this.mailSenderProvider = mailSenderProvider;
        this.notificationTaskExecutor = notificationTaskExecutor;
        this.transactionManager = transactionManager;
    }

    @PostConstruct
    void initRequiresNewTemplate() {
        requiresNewTemplate = new TransactionTemplate(transactionManager);
        requiresNewTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> listForEmployee(Long employeeId, Pageable pageable) {
        return notificationRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public void markRead(Long notificationId, Long employeeId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (n.getEmployeeId() == null || !n.getEmployeeId().equals(employeeId)) {
            throw new org.springframework.security.access.AccessDeniedException("Not your notification");
        }
        n.setReadFlag(true);
    }

    /**
     * Thông báo bảng lương đã được tạo (US15 + US17).
     */
    @Transactional
    public void notifySalaryGenerated(Long employeeId, String email, String fullName, String yearMonth, BigDecimal finalSalary) {
        String title = "Bảng lương tháng " + yearMonth;
        String body = "Xin chào " + fullName + ",\n\n"
                + "Hệ thống đã ghi nhận bảng lương tháng " + yearMonth + ".\n"
                + "Lương thực nhận (demo): " + finalSalary + "\n\n"
                + "— HRM System";
        createAndDispatch(employeeId, email, title, body, NotificationType.SALARY);
    }

    /**
     * Thông báo kết quả đơn nghỉ phép (US17).
     */
    @Transactional
    public void notifyLeaveDecision(
            Long employeeId,
            String email,
            String fullName,
            boolean approved,
            LocalDate start,
            LocalDate end
    ) {
        String title = approved ? "Đơn nghỉ phép đã được duyệt" : "Đơn nghỉ phép bị từ chối";
        String body = "Xin chào " + fullName + ",\n\n"
                + (approved
                ? "Đơn nghỉ từ " + start + " đến " + end + " đã được phê duyệt.\n"
                : "Đơn nghỉ từ " + start + " đến " + end + " không được phê duyệt.\n")
                + "\n— HRM System";
        createAndDispatch(employeeId, email, title, body, NotificationType.LEAVE);
    }

    /**
     * Thông báo đánh giá hiệu suất đã duyệt (US17).
     */
    @Transactional
    public void notifyPerformanceApproved(Long employeeId, String email, String fullName, int year, int quarter) {
        String title = "Đánh giá hiệu suất Q" + quarter + "-" + year + " đã được duyệt";
        String body = "Xin chào " + fullName + ",\n\n"
                + "Đánh giá hiệu suất quý " + quarter + "/" + year + " đã ở trạng thái APPROVED.\n\n"
                + "— HRM System";
        createAndDispatch(employeeId, email, title, body, NotificationType.PERFORMANCE);
    }

    @Transactional
    public Notification createAndDispatch(
            Long employeeId,
            String receiverEmail,
            String title,
            String content,
            NotificationType type
    ) {
        Notification n = new Notification();
        n.setEmployeeId(employeeId);
        n.setTitle(title);
        n.setContent(content);
        n.setReceiverEmail(receiverEmail);
        n.setNotificationType(type);
        n.setReadFlag(false);
        n.setStatus(NotificationDeliveryStatus.PENDING);
        notificationRepository.save(n);
        dispatchEmail(n);
        return n;
    }

    private void dispatchEmail(Notification n) {
        if (!mailSendEnabled) {
            n.setStatus(NotificationDeliveryStatus.SENT);
            n.setSentAt(LocalDateTime.now());
            log.info("[HRM-MAIL] SKIP smtp (app.mail.send-enabled=false) id={} marked SENT", n.getId());
            return;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            n.setStatus(NotificationDeliveryStatus.FAILED);
            log.warn("[HRM-MAIL] FAIL no JavaMailSender bean id={}", n.getId());
            return;
        }
        if (n.getReceiverEmail() == null || n.getReceiverEmail().isBlank()) {
            n.setStatus(NotificationDeliveryStatus.FAILED);
            log.warn("[HRM-MAIL] FAIL empty employee email id={}", n.getId());
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(mailFrom);
            msg.setTo(n.getReceiverEmail());
            msg.setSubject(n.getTitle());
            msg.setText(n.getContent() != null ? n.getContent() : "");
            log.info("[HRM-MAIL] SEND start id={} to={}", n.getId(), n.getReceiverEmail());
            sender.send(msg);
            n.setStatus(NotificationDeliveryStatus.SENT);
            n.setSentAt(LocalDateTime.now());
            log.info("[HRM-MAIL] SEND ok id={} to={}", n.getId(), n.getReceiverEmail());
        } catch (MailException ex) {
            log.error("[HRM-MAIL] SEND fail id={}: {}", n.getId(), ex.getMessage());
            n.setStatus(NotificationDeliveryStatus.FAILED);
        } catch (Exception ex) {
            log.error("[HRM-MAIL] SEND error id={}: {}", n.getId(), ex.toString(), ex);
            n.setStatus(NotificationDeliveryStatus.FAILED);
        }
    }

    private NotificationResponse toResponse(Notification n) {
        NotificationResponse r = new NotificationResponse();
        r.setId(n.getId());
        r.setEmployeeId(n.getEmployeeId());
        r.setTitle(n.getTitle());
        r.setContent(n.getContent());
        r.setReceiverEmail(n.getReceiverEmail());
        r.setNotificationType(n.getNotificationType());
        r.setDeliveryStatus(n.getStatus());
        r.setRead(n.isReadFlag());
        r.setSentAt(n.getSentAt());
        r.setCreatedAt(n.getCreatedAt());
        return r;
    }
}

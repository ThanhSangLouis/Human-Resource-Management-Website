package org.example.hrmsystem.service;

import org.example.hrmsystem.model.Employee;
import org.example.hrmsystem.model.EmployeeStatus;
import org.example.hrmsystem.model.LeaveStatus;
import org.example.hrmsystem.repository.EmployeeRepository;
import org.example.hrmsystem.repository.LeaveRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Đồng bộ {@link EmployeeStatus} với đơn nghỉ đã duyệt:
 * <p>
 * Điều kiện INACTIVE: tồn tại đơn {@link LeaveStatus#APPROVED} sao cho
 * {@code startDate <= ngày tham chiếu <= endDate} (cả hai cận inclusive).
 * Ngày tham chiếu = <strong>ngày hiện tại theo múi giờ ứng dụng</strong>
 * ({@code spring.jackson.time-zone}, mặc định {@code Asia/Ho_Chi_Minh}), khớp JDBC/Jackson.
 * <p>
 * Hệ thống chỉ lưu {@code DATE} cho đơn nghỉ (không có giờ phút); một ngày nghỉ là cả ngày theo lịch đó.
 */
@Service
public class EmployeeEmploymentLeaveSyncService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeEmploymentLeaveSyncService.class);

    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ZoneId applicationZone;

    public EmployeeEmploymentLeaveSyncService(
            EmployeeRepository employeeRepository,
            LeaveRequestRepository leaveRequestRepository,
            @Value("${spring.jackson.time-zone:Asia/Ho_Chi_Minh}") String jacksonTimeZone
    ) {
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.applicationZone = ZoneId.of(jacksonTimeZone);
    }

    /** Ngày “hôm nay” theo múi giờ cấu hình (VN). */
    public LocalDate todayInApplicationZone() {
        return LocalDate.now(applicationZone);
    }

    /**
     * Có ít nhất một đơn APPROVED mà {@code refDate} nằm trong [start_date, end_date] của đơn.
     */
    public boolean hasApprovedLeaveOn(LocalDate refDate, Long employeeId) {
        return leaveRequestRepository.countApprovedLeavesCoveringDate(
                employeeId,
                LeaveStatus.APPROVED,
                refDate
        ) > 0;
    }

    @Transactional
    public void syncEmploymentStatusForEmployee(Long employeeId) {
        LocalDate ref = todayInApplicationZone();
        employeeRepository.findById(employeeId).ifPresent(emp -> applyStatusForDate(emp, ref));
    }

    @Transactional
    public void syncAllEmployees() {
        LocalDate ref = todayInApplicationZone();
        for (Employee emp : employeeRepository.findAll()) {
            applyStatusForDate(emp, ref);
        }
    }

    private void applyStatusForDate(Employee emp, LocalDate refDate) {
        if (emp.getStatus() == EmployeeStatus.RESIGNED) {
            return;
        }
        boolean onApprovedLeave = hasApprovedLeaveOn(refDate, emp.getId());
        EmployeeStatus next = onApprovedLeave ? EmployeeStatus.INACTIVE : EmployeeStatus.ACTIVE;
        if (emp.getStatus() != next) {
            emp.setStatus(next);
            employeeRepository.save(emp);
            log.debug(
                    "employeeId={} status -> {} (refDate={} zone={} onApprovedLeave={})",
                    emp.getId(), next, refDate, applicationZone, onApprovedLeave
            );
        }
    }

    @Scheduled(cron = "0 0 1 * * *", zone = "${spring.jackson.time-zone:Asia/Ho_Chi_Minh}")
    @Transactional
    public void scheduledDailySync() {
        LocalDate ref = todayInApplicationZone();
        syncAllEmployees();
        log.info("Daily employment/leave status sync completed for refDate={} ({})", ref, applicationZone);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void syncOnStartup() {
        syncAllEmployees();
        log.info("Startup employment/leave status sync completed (zone={})", applicationZone);
    }
}

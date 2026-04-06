package org.example.hrmsystem.service;

import org.example.hrmsystem.dto.LeaveRequestCreateDto;
import org.example.hrmsystem.dto.LeaveRequestResponse;
import org.example.hrmsystem.exception.ResourceNotFoundException;
import org.example.hrmsystem.model.Attendance;
import org.example.hrmsystem.model.Employee;
import org.example.hrmsystem.model.LeaveRequest;
import org.example.hrmsystem.model.LeaveStatus;
import org.example.hrmsystem.model.LeaveType;
import org.example.hrmsystem.model.Role;
import org.example.hrmsystem.model.UserAccount;
import org.example.hrmsystem.repository.AttendanceRepository;
import org.example.hrmsystem.repository.DepartmentRepository;
import org.example.hrmsystem.repository.EmployeeRepository;
import org.example.hrmsystem.repository.LeaveRequestRepository;
import org.example.hrmsystem.repository.UserAccountRepository;
import org.example.hrmsystem.security.AppUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class LeaveRequestService {

    private static final Logger log = LoggerFactory.getLogger(LeaveRequestService.class);

    private final LeaveRequestRepository leaveRequestRepository;
    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final ManagerEmployeeScopeService managerEmployeeScopeService;
    private final AttendanceLeaveSyncService attendanceLeaveSyncService;
    private final NotificationService notificationService;
    private final UserAccountRepository userAccountRepository;
    private final DepartmentRepository departmentRepository;
    private final TaskExecutor notificationTaskExecutor;

    public LeaveRequestService(
            LeaveRequestRepository leaveRequestRepository,
            AttendanceRepository attendanceRepository,
            EmployeeRepository employeeRepository,
            ManagerEmployeeScopeService managerEmployeeScopeService,
            AttendanceLeaveSyncService attendanceLeaveSyncService,
            NotificationService notificationService,
            UserAccountRepository userAccountRepository,
            DepartmentRepository departmentRepository,
            @Qualifier("notificationTaskExecutor") TaskExecutor notificationTaskExecutor
    ) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.attendanceRepository = attendanceRepository;
        this.employeeRepository = employeeRepository;
        this.managerEmployeeScopeService = managerEmployeeScopeService;
        this.attendanceLeaveSyncService = attendanceLeaveSyncService;
        this.notificationService = notificationService;
        this.userAccountRepository = userAccountRepository;
        this.departmentRepository = departmentRepository;
        this.notificationTaskExecutor = notificationTaskExecutor;
    }

    @Transactional
    public LeaveRequestResponse create(Long employeeId, LeaveRequestCreateDto dto) {
        LocalDate today = LocalDate.now();

        LeaveType leaveType;
        try {
            leaveType = LeaveType.valueOf(dto.getLeaveType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid leave type: " + dto.getLeaveType());
        }

        if (dto.getStartDate().isBefore(today)) {
            throw new IllegalArgumentException("Start date cannot be in the past");
        }
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new IllegalArgumentException("End date must be on or after start date");
        }

        boolean hasOverlap = leaveRequestRepository
            .existsByEmployeeIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                employeeId,
                List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED),
                dto.getEndDate(),
                dto.getStartDate()
            );
        if (hasOverlap) {
            throw new IllegalArgumentException(
                "You already have a pending or approved leave request that overlaps with these dates"
            );
        }
        assertNoAttendanceConflict(employeeId, dto.getStartDate(), dto.getEndDate());

        int totalDays = countWorkingDays(dto.getStartDate(), dto.getEndDate());

        LeaveRequest request = new LeaveRequest();
        request.setEmployeeId(employeeId);
        request.setLeaveType(leaveType);
        request.setStartDate(dto.getStartDate());
        request.setEndDate(dto.getEndDate());
        request.setTotalDays(totalDays);
        request.setReason(dto.getReason() != null ? dto.getReason().trim() : null);
        request.setStatus(LeaveStatus.PENDING);

        LeaveRequest saved = leaveRequestRepository.save(request);
        log.info("Employee {} submitted leave request id={} ({} to {}, {} days)",
            employeeId, saved.getId(), saved.getStartDate(), saved.getEndDate(), saved.getTotalDays());

        String employeeName = resolveEmployeeName(employeeId);
        return toResponse(saved, employeeName, "Leave request submitted successfully");
    }

    public Map<String, Object> getMyLeaves(Long employeeId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<LeaveRequest> pageResult = leaveRequestRepository
            .findByEmployeeIdOrderByCreatedAtDesc(employeeId, pageable);

        List<LeaveRequestResponse> content = pageResult.getContent().stream()
            .map(lr -> toResponse(lr, resolveEmployeeName(lr.getEmployeeId()), null))
            .toList();

        return Map.of(
            "content", content,
            "page", pageResult.getNumber(),
            "size", pageResult.getSize(),
            "totalElements", pageResult.getTotalElements(),
            "totalPages", pageResult.getTotalPages(),
            "last", pageResult.isLast()
        );
    }

    /**
     * Hàng chờ theo phân cấp duyệt:
     * <ul>
     *   <li>MANAGER — đơn của EMPLOYEE (hoặc chưa có user) trong phòng quản lý</li>
     *   <li>HR — đơn của MANAGER hoặc ADMIN</li>
     *   <li>ADMIN — đơn của MANAGER hoặc HR</li>
     * </ul>
     */
    public Map<String, Object> listPending(AppUserDetails reviewer, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Role role = Role.valueOf(reviewer.getRole());
        if (role == Role.EMPLOYEE) {
            throw new AccessDeniedException("Employees cannot view the approval queue");
        }

        Page<LeaveRequest> pageResult;
        if (role == Role.MANAGER) {
            Long mgrKey = resolveActorEmployeeKey(reviewer);
            Set<Long> queueIds = managerEmployeeScopeService.managedEmployeeApplicantIds(mgrKey);
            if (queueIds.isEmpty()) {
                pageResult = Page.empty(pageable);
            } else {
                pageResult = leaveRequestRepository.findByStatusAndEmployeeIdInOrderByCreatedAtDesc(
                        LeaveStatus.PENDING, queueIds, pageable);
            }
        } else if (role == Role.HR) {
            pageResult = leaveRequestRepository.findByStatusAndApplicantAccountRolesIn(
                    LeaveStatus.PENDING, List.of(Role.MANAGER, Role.ADMIN), pageable);
        } else if (role == Role.ADMIN) {
            pageResult = leaveRequestRepository.findByStatusAndApplicantAccountRolesIn(
                    LeaveStatus.PENDING, List.of(Role.MANAGER, Role.HR), pageable);
        } else {
            throw new IllegalStateException("Unexpected role for approval queue: " + role);
        }

        List<LeaveRequestResponse> content = pageResult.getContent().stream()
                .map(lr -> toResponse(lr, resolveEmployeeName(lr.getEmployeeId()), null))
                .toList();

        Map<String, Object> out = new HashMap<>();
        out.put("content", content);
        out.put("page", pageResult.getNumber());
        out.put("size", pageResult.getSize());
        out.put("totalElements", pageResult.getTotalElements());
        out.put("totalPages", pageResult.getTotalPages());
        out.put("last", pageResult.isLast());
        return out;
    }

    @Transactional
    public LeaveRequestResponse approve(Long requestId, AppUserDetails reviewer) {
        LeaveRequest req = leaveRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found: " + requestId));
        if (req.getStatus() != LeaveStatus.PENDING) {
            throw new IllegalArgumentException("Only PENDING requests can be approved");
        }
        assertCanReview(req, reviewer);

        LocalDateTime now = LocalDateTime.now();
        req.setStatus(LeaveStatus.APPROVED);
        req.setApprovedBy(reviewer.getUserId());
        req.setApprovedAt(now);
        leaveRequestRepository.save(req);

        attendanceLeaveSyncService.syncOnLeaveForApprovedRange(
                req.getEmployeeId(), req.getStartDate(), req.getEndDate());

        employeeRepository.findById(req.getEmployeeId()).ifPresent(emp ->
                scheduleLeaveDecisionNotifyAfterCommit(
                        emp.getId(),
                        emp.getEmail(),
                        emp.getFullName(),
                        true,
                        req.getStartDate(),
                        req.getEndDate()
                ));

        log.info("Leave request {} APPROVED by user {} — attendance ON_LEAVE synced", requestId, reviewer.getUserId());
        return toResponse(req, resolveEmployeeName(req.getEmployeeId()), "Leave approved");
    }

    @Transactional
    public LeaveRequestResponse reject(Long requestId, AppUserDetails reviewer) {
        LeaveRequest req = leaveRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found: " + requestId));
        if (req.getStatus() != LeaveStatus.PENDING) {
            throw new IllegalArgumentException("Only PENDING requests can be rejected");
        }
        assertCanReview(req, reviewer);

        LocalDateTime now = LocalDateTime.now();
        req.setStatus(LeaveStatus.REJECTED);
        req.setApprovedBy(reviewer.getUserId());
        req.setApprovedAt(now);
        leaveRequestRepository.save(req);

        employeeRepository.findById(req.getEmployeeId()).ifPresent(emp -> {
            log.info("[HRM-MAIL] leave reject notify employeeId={} email={}", emp.getId(), emp.getEmail());
            scheduleLeaveDecisionNotifyAfterCommit(
                    emp.getId(),
                    emp.getEmail(),
                    emp.getFullName(),
                    false,
                    req.getStartDate(),
                    req.getEndDate()
            );
        });

        log.info("Leave request {} REJECTED by user {}", requestId, reviewer.getUserId());
        return toResponse(req, resolveEmployeeName(req.getEmployeeId()), "Leave rejected");
    }

    private void assertCanReview(LeaveRequest req, AppUserDetails reviewer) {
        Role reviewerRole = Role.valueOf(reviewer.getRole());
        if (reviewerRole == Role.EMPLOYEE) {
            throw new AccessDeniedException("Employees cannot review leave requests");
        }
        Long applicantId = req.getEmployeeId();
        if (isSelfLeaveReview(applicantId, reviewer)) {
            throw new AccessDeniedException("Cannot review your own leave request");
        }
        Role applicantRole = resolveApplicantRole(applicantId);
        Long reviewerKey = resolveActorEmployeeKey(reviewer);

        switch (applicantRole) {
            case EMPLOYEE -> {
                if (reviewerRole == Role.MANAGER) {
                    if (managerEmployeeScopeService.managesEmployee(reviewerKey, applicantId)) {
                        return;
                    }
                    throw new AccessDeniedException("Only your department manager can approve this request");
                }
                if (reviewerRole == Role.HR || reviewerRole == Role.ADMIN) {
                    if (lacksDepartmentManager(applicantId)) {
                        return;
                    }
                    throw new AccessDeniedException("This request must be approved by the department manager");
                }
                throw new AccessDeniedException("Not allowed to review this leave request");
            }
            case MANAGER -> {
                if (reviewerRole == Role.HR || reviewerRole == Role.ADMIN) {
                    return;
                }
                throw new AccessDeniedException("Manager leave requests are approved by HR or Admin");
            }
            case HR -> {
                if (reviewerRole == Role.ADMIN) {
                    return;
                }
                throw new AccessDeniedException("HR leave requests are approved by Admin only");
            }
            case ADMIN -> {
                if (reviewerRole == Role.HR) {
                    return;
                }
                throw new AccessDeniedException("Admin leave requests are approved by HR only");
            }
        }
    }

    private boolean isSelfLeaveReview(Long applicantEmployeeId, AppUserDetails reviewer) {
        if (reviewer.getEmployeeId() != null && reviewer.getEmployeeId().equals(applicantEmployeeId)) {
            return true;
        }
        return reviewer.getUserId() != null && reviewer.getUserId().equals(applicantEmployeeId);
    }

    private Role resolveApplicantRole(Long employeeId) {
        return userAccountRepository.findByEmployeeId(employeeId)
                .map(UserAccount::getRole)
                .orElse(Role.EMPLOYEE);
    }

    /** Phòng không gắn trưởng phòng → HR/Admin được duyệt thay cho cấp nhân viên. */
    private boolean lacksDepartmentManager(Long employeeId) {
        Optional<Employee> emp = employeeRepository.findById(employeeId);
        if (emp.isEmpty()) {
            return true;
        }
        Long deptId = emp.get().getDepartmentId();
        if (deptId == null) {
            return true;
        }
        return departmentRepository.findById(deptId)
                .map(d -> d.getManagerId() == null)
                .orElse(true);
    }

    /** Khóa giống chấm công: employee_id hoặc user id fallback. */
    private static Long resolveActorEmployeeKey(AppUserDetails user) {
        Long eid = user.getEmployeeId();
        return eid != null ? eid : user.getUserId();
    }

    private String resolveEmployeeName(Long employeeId) {
        return employeeRepository.findById(employeeId)
            .map(Employee::getFullName)
            .orElse("Employee #" + employeeId);
    }

    private int countWorkingDays(LocalDate start, LocalDate end) {
        int count = 0;
        LocalDate current = start;
        while (!current.isAfter(end)) {
            if (current.getDayOfWeek() != DayOfWeek.SUNDAY) {
                count++;
            }
            current = current.plusDays(1);
        }
        return count;
    }

    /**
     * Gửi email/notification sau khi transaction duyệt commit — phản hồi API nhanh, SMTP không chặn HTTP.
     */
    private void scheduleLeaveDecisionNotifyAfterCommit(
            Long employeeId,
            String email,
            String fullName,
            boolean approved,
            LocalDate start,
            LocalDate end
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            notificationTaskExecutor.execute(() -> runLeaveNotifySafe(employeeId, email, fullName, approved, start, end));
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notificationTaskExecutor.execute(() -> runLeaveNotifySafe(employeeId, email, fullName, approved, start, end));
            }
        });
    }

    private void runLeaveNotifySafe(
            Long employeeId,
            String email,
            String fullName,
            boolean approved,
            LocalDate start,
            LocalDate end
    ) {
        try {
            notificationService.notifyLeaveDecision(employeeId, email, fullName, approved, start, end);
        } catch (Exception ex) {
            log.warn("Leave decision notification failed employeeId={}: {}", employeeId, ex.getMessage());
        }
    }

    private void assertNoAttendanceConflict(Long employeeId, LocalDate startDate, LocalDate endDate) {
        List<Attendance> attendanceInRange = attendanceRepository
                .findByEmployeeIdAndAttendanceDateBetween(employeeId, startDate, endDate);
        boolean hasCheckInOrCheckOut = attendanceInRange.stream()
                .anyMatch(a -> a.getCheckIn() != null || a.getCheckOut() != null);
        if (hasCheckInOrCheckOut) {
            throw new IllegalArgumentException(
                    "Invalid leave request: attendance already exists (check-in/check-out) in selected date range");
        }
    }

    private LeaveRequestResponse toResponse(LeaveRequest lr, String employeeName, String message) {
        return new LeaveRequestResponse(
            lr.getId(),
            lr.getEmployeeId(),
            employeeName,
            lr.getLeaveType(),
            lr.getStartDate(),
            lr.getEndDate(),
            lr.getTotalDays(),
            lr.getReason(),
            lr.getStatus(),
            lr.getApprovedBy(),
            lr.getApprovedAt(),
            lr.getCreatedAt(),
            message
        );
    }
}

package org.example.hrmsystem.service;

import org.example.hrmsystem.dto.LeaveRequestCreateDto;
import org.example.hrmsystem.dto.LeaveRequestResponse;
import org.example.hrmsystem.exception.ResourceNotFoundException;
import org.example.hrmsystem.model.Employee;
import org.example.hrmsystem.model.LeaveRequest;
import org.example.hrmsystem.model.LeaveStatus;
import org.example.hrmsystem.model.LeaveType;
import org.example.hrmsystem.model.Role;
import org.example.hrmsystem.repository.EmployeeRepository;
import org.example.hrmsystem.repository.LeaveRequestRepository;
import org.example.hrmsystem.security.AppUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class LeaveRequestService {

    private static final Logger log = LoggerFactory.getLogger(LeaveRequestService.class);

    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final ManagerEmployeeScopeService managerEmployeeScopeService;
    private final AttendanceLeaveSyncService attendanceLeaveSyncService;

    public LeaveRequestService(
            LeaveRequestRepository leaveRequestRepository,
            EmployeeRepository employeeRepository,
            ManagerEmployeeScopeService managerEmployeeScopeService,
            AttendanceLeaveSyncService attendanceLeaveSyncService
    ) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.employeeRepository = employeeRepository;
        this.managerEmployeeScopeService = managerEmployeeScopeService;
        this.attendanceLeaveSyncService = attendanceLeaveSyncService;
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

        String employeeName = resolveEmployeeName(employeeId);

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
     * HR / ADMIN: toàn bộ đơn chờ duyệt. MANAGER: chỉ nhân viên thuộc phòng quản lý.
     */
    public Map<String, Object> listPending(AppUserDetails reviewer, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Role role = Role.valueOf(reviewer.getRole());
        if (role == Role.EMPLOYEE) {
            throw new AccessDeniedException("Employees cannot view the approval queue");
        }

        Page<LeaveRequest> pageResult;
        if (role == Role.MANAGER) {
            Long mgrKey = resolveActorEmployeeKey(reviewer);
            Set<Long> visible = managerEmployeeScopeService.visibleEmployeeIdsForManager(mgrKey);
            if (visible.isEmpty()) {
                pageResult = Page.empty(pageable);
            } else {
                pageResult = leaveRequestRepository.findByStatusAndEmployeeIdInOrderByCreatedAtDesc(
                        LeaveStatus.PENDING, visible, pageable);
            }
        } else {
            pageResult = leaveRequestRepository.findByStatusOrderByCreatedAtDesc(
                    LeaveStatus.PENDING, pageable);
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
        LeaveRequest req = leaveRequestRepository.findById(requestId)
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

        log.info("Leave request {} APPROVED by user {} — attendance ON_LEAVE synced", requestId, reviewer.getUserId());
        return toResponse(req, resolveEmployeeName(req.getEmployeeId()), "Leave approved");
    }

    @Transactional
    public LeaveRequestResponse reject(Long requestId, AppUserDetails reviewer) {
        LeaveRequest req = leaveRequestRepository.findById(requestId)
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

        log.info("Leave request {} REJECTED by user {}", requestId, reviewer.getUserId());
        return toResponse(req, resolveEmployeeName(req.getEmployeeId()), "Leave rejected");
    }

    private void assertCanReview(LeaveRequest req, AppUserDetails reviewer) {
        Role role = Role.valueOf(reviewer.getRole());
        if (role == Role.EMPLOYEE) {
            throw new AccessDeniedException("Employees cannot review leave requests");
        }
        if (role == Role.ADMIN || role == Role.HR) {
            return;
        }
        if (role == Role.MANAGER) {
            Long mgrKey = resolveActorEmployeeKey(reviewer);
            if (!managerEmployeeScopeService.managesEmployee(mgrKey, req.getEmployeeId())) {
                throw new AccessDeniedException("You can only review leave for employees in your department");
            }
            return;
        }
        throw new AccessDeniedException("Not allowed to review leave requests");
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

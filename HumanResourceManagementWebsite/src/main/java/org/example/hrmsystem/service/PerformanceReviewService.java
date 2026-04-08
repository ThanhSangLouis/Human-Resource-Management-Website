package org.example.hrmsystem.service;

import org.example.hrmsystem.dto.PerformanceReviewCreateRequest;
import org.example.hrmsystem.dto.PerformanceReviewResponse;
import org.example.hrmsystem.dto.PerformanceReviewUpdateRequest;
import org.example.hrmsystem.exception.DuplicateResourceException;
import org.example.hrmsystem.exception.ResourceNotFoundException;
import org.example.hrmsystem.model.Employee;
import org.example.hrmsystem.model.PerformanceReview;
import org.example.hrmsystem.model.PerformanceReviewStatus;
import org.example.hrmsystem.model.Role;
import org.example.hrmsystem.repository.EmployeeRepository;
import org.example.hrmsystem.repository.PerformanceReviewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PerformanceReviewService {

    private final PerformanceReviewRepository performanceReviewRepository;
    private final EmployeeRepository employeeRepository;
    private final ManagerEmployeeScopeService managerEmployeeScopeService;
    private final NotificationService notificationService;

    public PerformanceReviewService(
            PerformanceReviewRepository performanceReviewRepository,
            EmployeeRepository employeeRepository,
            ManagerEmployeeScopeService managerEmployeeScopeService,
            NotificationService notificationService
    ) {
        this.performanceReviewRepository = performanceReviewRepository;
        this.employeeRepository = employeeRepository;
        this.managerEmployeeScopeService = managerEmployeeScopeService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public Page<PerformanceReviewResponse> list(Integer year, Integer quarter, Pageable pageable) {
        Page<PerformanceReview> page;
        if (year != null && quarter != null) {
            page = performanceReviewRepository.findByReviewYearAndReviewQuarter(year, quarter, pageable);
        } else if (year != null) {
            page = performanceReviewRepository.findByReviewYear(year, pageable);
        } else {
            page = performanceReviewRepository.findAll(pageable);
        }
        Map<Long, Employee> em = loadEmployees(page.getContent().stream()
                .map(PerformanceReview::getEmployeeId)
                .collect(Collectors.toSet()));
        return page.map(r -> toResponse(r, em.get(r.getEmployeeId())));
    }

    @Transactional(readOnly = true)
    public PerformanceReviewResponse getById(Long id) {
        PerformanceReview r = performanceReviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Performance review not found"));
        Employee e = employeeRepository.findById(r.getEmployeeId()).orElse(null);
        return toResponse(r, e);
    }

    @Transactional
    public PerformanceReviewResponse create(
            PerformanceReviewCreateRequest req,
            Long reviewerUserId,
            Role role,
            Long actorEmployeeKey
    ) {
        if (performanceReviewRepository.existsByEmployeeIdAndReviewYearAndReviewQuarter(
                req.getEmployeeId(), req.getReviewYear(), req.getReviewQuarter())) {
            throw new DuplicateResourceException(
                    "Đã tồn tại đánh giá cho nhân viên này trong quý " + req.getReviewYear() + "-Q" + req.getReviewQuarter());
        }
        assertCanWriteReviewForEmployee(role, actorEmployeeKey, req.getEmployeeId());

        PerformanceReview r = new PerformanceReview();
        r.setEmployeeId(req.getEmployeeId());
        r.setReviewerId(reviewerUserId);
        r.setReviewYear(req.getReviewYear());
        r.setReviewQuarter(req.getReviewQuarter());
        if (req.getScore() != null) {
            validateScore(req.getScore());
            r.setScore(req.getScore());
        }
        r.setReviewComment(req.getReviewComment());
        r.setStatus(PerformanceReviewStatus.DRAFT);
        r = performanceReviewRepository.save(r);
        Employee e = employeeRepository.findById(r.getEmployeeId()).orElse(null);
        return toResponse(r, e);
    }

    @Transactional
    public PerformanceReviewResponse updateDraft(
            Long id,
            PerformanceReviewUpdateRequest req,
            Role role,
            Long actorEmployeeKey
    ) {
        PerformanceReview r = performanceReviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Performance review not found"));
        if (r.getStatus() != PerformanceReviewStatus.DRAFT) {
            throw new IllegalArgumentException("Chỉ chỉnh được bản ghi ở trạng thái DRAFT");
        }
        assertCanWriteReviewForEmployee(role, actorEmployeeKey, r.getEmployeeId());

        if (req.getScore() != null) {
            validateScore(req.getScore());
            r.setScore(req.getScore());
        }
        if (req.getReviewComment() != null) {
            r.setReviewComment(req.getReviewComment());
        }
        r = performanceReviewRepository.save(r);
        Employee e = employeeRepository.findById(r.getEmployeeId()).orElse(null);
        return toResponse(r, e);
    }

    @Transactional
    public PerformanceReviewResponse submit(Long id, Role role, Long actorEmployeeKey) {
        PerformanceReview r = performanceReviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Performance review not found"));
        if (r.getStatus() != PerformanceReviewStatus.DRAFT) {
            throw new IllegalArgumentException("Chỉ gửi duyệt từ trạng thái DRAFT");
        }
        assertCanWriteReviewForEmployee(role, actorEmployeeKey, r.getEmployeeId());
        if (r.getScore() == null) {
            throw new IllegalArgumentException("Điểm (0–100) bắt buộc khi gửi duyệt");
        }
        validateScore(r.getScore());
        r.setStatus(PerformanceReviewStatus.SUBMITTED);
        r.setReviewDate(LocalDate.now());
        r = performanceReviewRepository.save(r);
        Employee e = employeeRepository.findById(r.getEmployeeId()).orElse(null);
        return toResponse(r, e);
    }

    @Transactional
    public PerformanceReviewResponse approve(Long id, Role role, Long actorEmployeeKey) {
        PerformanceReview r = performanceReviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Performance review not found"));
        if (r.getStatus() != PerformanceReviewStatus.SUBMITTED) {
            throw new IllegalArgumentException("Chỉ duyệt khi trạng thái SUBMITTED");
        }
        assertCanApprove(role);

        r.setStatus(PerformanceReviewStatus.APPROVED);
        r.setReviewDate(LocalDate.now());
        r = performanceReviewRepository.save(r);

        Employee e = employeeRepository.findById(r.getEmployeeId()).orElse(null);
        if (e != null) {
            notificationService.notifyPerformanceApproved(
                    e.getId(),
                    e.getEmail(),
                    e.getFullName(),
                    r.getReviewYear(),
                    r.getReviewQuarter()
            );
        }
        return toResponse(r, e);
    }

    private void assertCanWriteReviewForEmployee(Role role, Long actorEmployeeKey, Long targetEmployeeId) {
        if (role == Role.ADMIN || role == Role.HR) {
            return;
        }
        if (role == Role.MANAGER) {
            if (!managerEmployeeScopeService.managesEmployee(actorEmployeeKey, targetEmployeeId)) {
                throw new AccessDeniedException("Manager chỉ tạo/sửa đánh giá cho nhân viên thuộc phạm vi phòng ban");
            }
            return;
        }
        throw new AccessDeniedException("Không có quyền tạo đánh giá");
    }

    /**
     * Phê duyệt chính thức (SUBMITTED → APPROVED): chỉ Admin / HR.
     * Quản lý trực tiếp chỉ tạo / sửa nháp và gửi duyệt; không phê duyệt cuối.
     */
    private void assertCanApprove(Role role) {
        if (role == Role.ADMIN || role == Role.HR) {
            return;
        }
        throw new AccessDeniedException("Chỉ Admin hoặc HR mới phê duyệt đánh giá chính thức");
    }

    private static void validateScore(int score) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("Điểm phải từ 0 đến 100");
        }
    }

    private Map<Long, Employee> loadEmployees(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return employeeRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Employee::getId, x -> x));
    }

    private PerformanceReviewResponse toResponse(PerformanceReview r, Employee emp) {
        PerformanceReviewResponse o = new PerformanceReviewResponse();
        o.setId(r.getId());
        o.setEmployeeId(r.getEmployeeId());
        o.setEmployeeName(emp != null ? emp.getFullName() : "—");
        o.setReviewerId(r.getReviewerId());
        o.setReviewYear(r.getReviewYear());
        o.setReviewQuarter(r.getReviewQuarter());
        o.setScore(r.getScore());
        o.setReviewComment(r.getReviewComment());
        o.setStatus(r.getStatus());
        o.setReviewDate(r.getReviewDate());
        o.setCreatedAt(r.getCreatedAt());
        o.setUpdatedAt(r.getUpdatedAt());
        return o;
    }
}

package org.example.hrmsystem.service;

import org.example.hrmsystem.dto.DepartmentRequest;
import org.example.hrmsystem.dto.DepartmentResponse;
import org.example.hrmsystem.exception.DuplicateResourceException;
import org.example.hrmsystem.exception.ResourceNotFoundException;
import org.example.hrmsystem.model.Department;
import org.example.hrmsystem.model.Employee;
import org.example.hrmsystem.model.Role;
import org.example.hrmsystem.repository.DepartmentRepository;
import org.example.hrmsystem.repository.EmployeeRepository;
import org.example.hrmsystem.security.AppUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final ManagerEmployeeScopeService managerEmployeeScopeService;

    public DepartmentService(DepartmentRepository departmentRepository,
                             EmployeeRepository employeeRepository,
                             ManagerEmployeeScopeService managerEmployeeScopeService) {
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
        this.managerEmployeeScopeService = managerEmployeeScopeService;
    }

    // ── Read ────────────────────────────────────────────────────────────────

    public Page<DepartmentResponse> findAll(String keyword, Pageable pageable) {
        Page<Department> page = (keyword != null && !keyword.isBlank())
                ? departmentRepository.findByNameContainingIgnoreCase(keyword.trim(), pageable)
                : departmentRepository.findAll(pageable);
        return page.map(this::toResponse);
    }

    public DepartmentResponse findById(Long id) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Phòng ban không tồn tại: id=" + id));
        return toResponse(dept);
    }

    public Page<DepartmentResponse> findAllForActor(AppUserDetails actor, String keyword, Pageable pageable) {
        Role role = Role.valueOf(actor.getRole());
        if (role == Role.ADMIN || role == Role.HR) {
            return findAll(keyword, pageable);
        }
        if (role == Role.MANAGER) {
            Long key = actor.getEmployeeId() != null ? actor.getEmployeeId() : actor.getUserId();
            var deptIds = managerEmployeeScopeService.managedDepartmentIds(key);
            if (deptIds.isEmpty()) {
                return Page.empty(pageable);
            }
            String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
            return departmentRepository.findByIdInWithOptionalKeyword(deptIds, kw, pageable).map(this::toResponse);
        }
        throw new AccessDeniedException("Không có quyền xem danh sách phòng ban");
    }

    public DepartmentResponse findByIdForActor(Long id, AppUserDetails actor) {
        Role role = Role.valueOf(actor.getRole());
        if (role == Role.ADMIN || role == Role.HR) {
            return findById(id);
        }
        if (role == Role.MANAGER) {
            Long key = actor.getEmployeeId() != null ? actor.getEmployeeId() : actor.getUserId();
            if (!managerEmployeeScopeService.managedDepartmentIds(key).contains(id)) {
                throw new AccessDeniedException("Bạn chỉ được xem phòng ban trong phạm vi quản lý");
            }
            return findById(id);
        }
        throw new AccessDeniedException("Không có quyền xem phòng ban");
    }

    // ── Create ──────────────────────────────────────────────────────────────

    @Transactional
    public DepartmentResponse create(DepartmentRequest request) {
        String name = request.getName().trim();
        if (departmentRepository.existsByName(name)) {
            throw new DuplicateResourceException("Tên phòng ban đã tồn tại: " + name);
        }
        Department dept = new Department();
        dept.setName(name);
        dept.setDescription(request.getDescription());
        validateManagerIdExists(request.getManagerId());
        dept.setManagerId(request.getManagerId());
        return toResponse(departmentRepository.save(dept));
    }

    // ── Update ──────────────────────────────────────────────────────────────

    @Transactional
    public DepartmentResponse update(Long id, DepartmentRequest request) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Phòng ban không tồn tại: id=" + id));

        String name = request.getName().trim();
        if (departmentRepository.existsByNameAndIdNot(name, id)) {
            throw new DuplicateResourceException("Tên phòng ban đã tồn tại: " + name);
        }

        dept.setName(name);
        dept.setDescription(request.getDescription());
        validateManagerForDepartment(request.getManagerId(), id);
        dept.setManagerId(request.getManagerId());
        return toResponse(departmentRepository.save(dept));
    }

    private void validateManagerIdExists(Long managerId) {
        if (managerId == null) {
            return;
        }
        employeeRepository.findById(managerId)
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên trưởng phòng không tồn tại (id=" + managerId + ")"));
    }

    /** Trưởng phòng phải là nhân viên đang thuộc đúng phòng ban (theo employees.department_id). */
    private void validateManagerForDepartment(Long managerId, Long departmentId) {
        if (managerId == null) {
            return;
        }
        Employee m = employeeRepository.findById(managerId)
                .orElseThrow(() -> new IllegalArgumentException("Nhân viên trưởng phòng không tồn tại (id=" + managerId + ")"));
        if (m.getDepartmentId() == null || !m.getDepartmentId().equals(departmentId)) {
            throw new IllegalArgumentException(
                    "Trưởng phòng phải là nhân viên thuộc phòng ban này (cập nhật phòng cho nhân viên trước khi gán)");
        }
    }

    // ── Delete ──────────────────────────────────────────────────────────────

    @Transactional
    public void delete(Long id) {
        if (!departmentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Phòng ban không tồn tại: id=" + id);
        }
        departmentRepository.deleteById(id);
    }

    // ── Mapping ─────────────────────────────────────────────────────────────

    private DepartmentResponse toResponse(Department dept) {
        String managerName = null;
        if (dept.getManagerId() != null) {
            managerName = employeeRepository.findById(dept.getManagerId())
                    .map(Employee::getFullName)
                    .orElse(null);
        }
        return new DepartmentResponse(
                dept.getId(),
                dept.getName(),
                dept.getDescription(),
                dept.getManagerId(),
                managerName,
                dept.getCreatedAt(),
                dept.getUpdatedAt()
        );
    }
}

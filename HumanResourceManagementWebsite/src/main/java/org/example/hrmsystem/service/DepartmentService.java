package org.example.hrmsystem.service;

import org.example.hrmsystem.dto.DepartmentRequest;
import org.example.hrmsystem.dto.DepartmentResponse;
import org.example.hrmsystem.exception.DuplicateResourceException;
import org.example.hrmsystem.exception.ResourceNotFoundException;
import org.example.hrmsystem.model.Department;
import org.example.hrmsystem.model.Employee;
import org.example.hrmsystem.repository.DepartmentRepository;
import org.example.hrmsystem.repository.EmployeeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;

    public DepartmentService(DepartmentRepository departmentRepository,
                              EmployeeRepository employeeRepository) {
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
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

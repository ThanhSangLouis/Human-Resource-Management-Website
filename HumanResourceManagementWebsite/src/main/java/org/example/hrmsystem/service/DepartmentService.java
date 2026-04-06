package org.example.hrmsystem.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.example.hrmsystem.dto.DepartmentRequest;
import org.example.hrmsystem.dto.DepartmentResponse;
import org.example.hrmsystem.exception.DuplicateResourceException;
import org.example.hrmsystem.exception.ResourceNotFoundException;
import org.example.hrmsystem.model.Department;
import org.example.hrmsystem.model.Employee;
import org.example.hrmsystem.model.Role;
import org.example.hrmsystem.repository.DepartmentRepository;
import org.example.hrmsystem.repository.EmployeeRepository;
import org.example.hrmsystem.repository.UserAccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final UserAccountRepository userAccountRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public DepartmentService(DepartmentRepository departmentRepository,
                              EmployeeRepository employeeRepository,
                              UserAccountRepository userAccountRepository) {
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
        this.userAccountRepository = userAccountRepository;
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
        Department saved = departmentRepository.save(dept);
        if (request.getManagerId() != null) {
            promoteToManager(request.getManagerId());
        }
        return toResponse(saved);
    }

    // ── Update (PUT) ─────────────────────────────────────────────────────────

    @Transactional
    public DepartmentResponse update(Long id, DepartmentRequest request) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Phòng ban không tồn tại: id=" + id));

        String name = request.getName().trim();
        if (departmentRepository.existsByNameAndIdNot(name, id)) {
            throw new DuplicateResourceException("Tên phòng ban đã tồn tại: " + name);
        }

        Long oldManagerId = dept.getManagerId();
        Long newManagerId = request.getManagerId();

        validateManagerForDepartment(newManagerId, id);

        dept.setName(name);
        dept.setDescription(request.getDescription());
        dept.setManagerId(newManagerId);
        departmentRepository.save(dept);
        entityManager.flush();

        syncRoleOnManagerChange(oldManagerId, newManagerId);

        // Refresh entity để trả về dữ liệu mới nhất từ DB
        entityManager.refresh(dept);
        return toResponse(dept);
    }

    private void validateManagerIdExists(Long managerId) {
        if (managerId == null) return;
        employeeRepository.findById(managerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Nhân viên trưởng phòng không tồn tại (id=" + managerId + ")"));
    }

    /** Trưởng phòng phải là nhân viên đang thuộc đúng phòng ban (theo employees.department_id). */
    private void validateManagerForDepartment(Long managerId, Long departmentId) {
        if (managerId == null) return;
        Employee m = employeeRepository.findById(managerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Nhân viên trưởng phòng không tồn tại (id=" + managerId + ")"));
        if (m.getDepartmentId() == null || !m.getDepartmentId().equals(departmentId)) {
            throw new IllegalArgumentException(
                    "Trưởng phòng phải là nhân viên thuộc phòng ban này (cập nhật phòng cho nhân viên trước khi gán)");
        }
    }

    // ── Assign Manager (PATCH) ───────────────────────────────────────────────

    /**
     * PATCH /api/departments/{id}/manager
     * Dùng native UPDATE trực tiếp để tránh JPA L1-cache dirty-read.
     * Đồng bộ role UserAccount:
     *   - Gán mới  → EMPLOYEE → MANAGER
     *   - Bỏ gán   → MANAGER → EMPLOYEE (nếu không còn quản lý phòng nào)
     */
    @Transactional
    public DepartmentResponse assignManager(Long departmentId, Long managerId) {
        // 1. Kiểm tra phòng ban tồn tại
        Department dept = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Phòng ban không tồn tại: id=" + departmentId));

        Long oldManagerId = dept.getManagerId();

        // 2. Validate nếu có gán manager mới
        if (managerId != null) {
            Employee m = employeeRepository.findById(managerId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Nhân viên không tồn tại: id=" + managerId));
            if (m.getDepartmentId() == null || !m.getDepartmentId().equals(departmentId)) {
                throw new IllegalArgumentException(
                        "Nhân viên (id=" + managerId + ") không thuộc phòng ban này. " +
                        "Vui lòng cập nhật phòng ban cho nhân viên trước khi gán làm trưởng phòng.");
            }
        }

        // 3. Native UPDATE trực tiếp xuống DB (bypass L1 cache hoàn toàn)
        departmentRepository.updateManagerId(departmentId, managerId);
        entityManager.flush();
        // Evict entity khỏi L1 cache để lần đọc sau lấy từ DB
        entityManager.clear();

        // 4. Đồng bộ role UserAccount
        syncRoleOnManagerChange(oldManagerId, managerId);

        // 5. Đọc lại từ DB để trả về response chính xác
        Department updated = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Phòng ban không tồn tại: id=" + departmentId));
        return toResponse(updated);
    }

    // ── Role Sync Helpers ────────────────────────────────────────────────────

    private void syncRoleOnManagerChange(Long oldManagerId, Long newManagerId) {
        if (newManagerId != null && !newManagerId.equals(oldManagerId)) {
            promoteToManager(newManagerId);
        }
        if (oldManagerId != null && !oldManagerId.equals(newManagerId)) {
            demoteIfNoLongerManager(oldManagerId);
        }
    }

    /**
     * Nâng role lên MANAGER nếu đang là EMPLOYEE.
     * ADMIN / HR không bị thay đổi.
     */
    private void promoteToManager(Long employeeId) {
        userAccountRepository.findByEmployeeId(employeeId).ifPresent(user -> {
            if (user.getRole() == Role.EMPLOYEE) {
                user.setRole(Role.MANAGER);
                userAccountRepository.save(user);
            }
        });
    }

    /**
     * Hạ role về EMPLOYEE nếu nhân viên không còn quản lý bất kỳ phòng nào.
     * Dùng JPQL count trực tiếp (không qua L1 cache) để tránh dirty-read.
     */
    private void demoteIfNoLongerManager(Long employeeId) {
        long count = departmentRepository.countByManagerId(employeeId);
        if (count > 0) return;

        userAccountRepository.findByEmployeeId(employeeId).ifPresent(user -> {
            if (user.getRole() == Role.MANAGER) {
                user.setRole(Role.EMPLOYEE);
                userAccountRepository.save(user);
            }
        });
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    @Transactional
    public void delete(Long id) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Phòng ban không tồn tại: id=" + id));
        Long managerId = dept.getManagerId();
        departmentRepository.deleteById(id);
        entityManager.flush();
        if (managerId != null) {
            demoteIfNoLongerManager(managerId);
        }
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

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

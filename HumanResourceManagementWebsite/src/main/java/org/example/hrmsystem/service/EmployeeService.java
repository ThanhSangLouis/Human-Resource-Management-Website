package org.example.hrmsystem.service;

import org.example.hrmsystem.dto.EmployeeRequest;
import org.example.hrmsystem.dto.EmployeeResponse;
import org.example.hrmsystem.exception.DuplicateResourceException;
import org.example.hrmsystem.exception.ResourceNotFoundException;
import org.example.hrmsystem.model.*;
import org.example.hrmsystem.repository.DepartmentRepository;
import org.example.hrmsystem.repository.EmployeeRepository;
import org.example.hrmsystem.repository.UserAccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeService {

    /** Mật khẩu ban đầu khi tạo user (nếu không gửi initialPassword); lưu DB đã hash BCrypt. */
    private static final String DEFAULT_INITIAL_PASSWORD = "123456";

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public EmployeeService(EmployeeRepository employeeRepository,
                           DepartmentRepository departmentRepository,
                           UserAccountRepository userAccountRepository,
                           PasswordEncoder passwordEncoder) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ── Read All (Search + Filter + Pagination) ──────────────────────────────

    public Page<EmployeeResponse> findAll(String keyword,
                                          String statusStr,
                                          Long departmentId,
                                          Pageable pageable) {
        EmployeeStatus status = null;
        if (statusStr != null && !statusStr.isBlank()) {
            try { status = EmployeeStatus.valueOf(statusStr.toUpperCase()); }
            catch (IllegalArgumentException ignored) {}
        }
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        return employeeRepository.search(kw, status, departmentId, pageable)
                .map(this::toResponse);
    }

    // ── Read One ─────────────────────────────────────────────────────────────

    public EmployeeResponse findById(Long id) {
        Employee emp = getOrThrow(id);
        return toResponse(emp);
    }

    // ── Create ───────────────────────────────────────────────────────────────

    @Transactional
    public EmployeeResponse create(EmployeeRequest req) {
        validateUnique(req.getEmployeeCode(), req.getEmail(), null);
        if (req.getDepartmentId() != null) validateDepartment(req.getDepartmentId());

        Employee emp = new Employee();
        applyFields(emp, req);
        Employee saved = employeeRepository.save(emp);
        createUserForNewEmployee(saved, req);
        return toResponse(saved);
    }

    // ── Update ───────────────────────────────────────────────────────────────

    @Transactional
    public EmployeeResponse update(Long id, EmployeeRequest req) {
        Employee emp = getOrThrow(id);
        validateUnique(req.getEmployeeCode(), req.getEmail(), id);
        if (req.getDepartmentId() != null) validateDepartment(req.getDepartmentId());

        applyFields(emp, req);
        return toResponse(employeeRepository.save(emp));
    }

    // ── Delete (soft: set RESIGNED) ───────────────────────────────────────────

    @Transactional
    public void delete(Long id) {
        Employee emp = getOrThrow(id);
        emp.setStatus(EmployeeStatus.RESIGNED);
        employeeRepository.save(emp);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Employee getOrThrow(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Nhân viên không tồn tại: id=" + id));
    }

    private void validateUnique(String code, String email, Long excludeId) {
        if (code != null && !code.isBlank()) {
            boolean dup = excludeId == null
                    ? employeeRepository.existsByEmployeeCode(code)
                    : employeeRepository.existsByEmployeeCodeAndIdNot(code, excludeId);
            if (dup) throw new DuplicateResourceException("Mã nhân viên đã tồn tại: " + code);
        }
        if (email != null && !email.isBlank()) {
            boolean dup = excludeId == null
                    ? employeeRepository.existsByEmail(email)
                    : employeeRepository.existsByEmailAndIdNot(email, excludeId);
            if (dup) throw new DuplicateResourceException("Email đã tồn tại: " + email);
        }
    }

    private void validateDepartment(Long deptId) {
        if (!departmentRepository.existsById(deptId)) {
            throw new IllegalArgumentException("Phòng ban không tồn tại: id=" + deptId);
        }
    }

    /**
     * Tạo tài khoản đăng nhập (role EMPLOYEE), gắn với nhân viên vừa tạo.
     * Username: email (chuẩn hóa chữ thường) nếu có, không thì mã nhân viên.
     * Mật khẩu: {@code initialPassword} nếu gửi lên, không thì mặc định 123456 (lưu đã hash BCrypt).
     */
    private void createUserForNewEmployee(Employee emp, EmployeeRequest req) {
        if (userAccountRepository.findByEmployeeId(emp.getId()).isPresent()) {
            return;
        }
        String username = resolveLoginUsername(emp);
        if (username.length() > 100) {
            throw new IllegalArgumentException(
                    "Email hoặc mã nhân viên dùng làm tên đăng nhập vượt quá 100 ký tự.");
        }
        if (userAccountRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Tên đăng nhập đã tồn tại: " + username);
        }
        String rawPassword = (req.getInitialPassword() != null && !req.getInitialPassword().isBlank())
                ? req.getInitialPassword().trim()
                : DEFAULT_INITIAL_PASSWORD;

        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(Role.EMPLOYEE);
        user.setEmployeeId(emp.getId());
        user.setActive(true);
        userAccountRepository.save(user);
    }

    private static String resolveLoginUsername(Employee emp) {
        if (emp.getEmail() != null && !emp.getEmail().isBlank()) {
            return emp.getEmail().trim().toLowerCase();
        }
        return emp.getEmployeeCode().trim();
    }

    private void applyFields(Employee emp, EmployeeRequest req) {
        emp.setEmployeeCode(req.getEmployeeCode());
        emp.setFullName(req.getFullName());
        emp.setEmail(req.getEmail());
        emp.setPhone(req.getPhone());
        emp.setPosition(req.getPosition());
        emp.setDepartmentId(req.getDepartmentId());
        emp.setSalaryBase(req.getSalaryBase());
        emp.setHireDate(req.getHireDate());
        emp.setDateOfBirth(req.getDateOfBirth());

        if (req.getGender() != null && !req.getGender().isBlank()) {
            try { emp.setGender(Gender.valueOf(req.getGender().toUpperCase())); }
            catch (IllegalArgumentException ignored) {}
        }

        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            try { emp.setStatus(EmployeeStatus.valueOf(req.getStatus().toUpperCase())); }
            catch (IllegalArgumentException ignored) {}
        } else if (emp.getStatus() == null) {
            emp.setStatus(EmployeeStatus.ACTIVE);
        }
    }

    private EmployeeResponse toResponse(Employee emp) {
        EmployeeResponse r = new EmployeeResponse();
        r.setId(emp.getId());
        r.setEmployeeCode(emp.getEmployeeCode());
        r.setFullName(emp.getFullName());
        r.setEmail(emp.getEmail());
        r.setPhone(emp.getPhone());
        r.setPosition(emp.getPosition());
        r.setGender(emp.getGender() != null ? emp.getGender().name() : null);
        r.setDateOfBirth(emp.getDateOfBirth());
        r.setAvatarUrl(emp.getAvatarUrl());
        r.setDepartmentId(emp.getDepartmentId());
        r.setSalaryBase(emp.getSalaryBase());
        r.setStatus(emp.getStatus() != null ? emp.getStatus().name() : null);
        r.setHireDate(emp.getHireDate());
        r.setCreatedAt(emp.getCreatedAt());
        r.setUpdatedAt(emp.getUpdatedAt());

        // Resolve department name
        if (emp.getDepartmentId() != null) {
            departmentRepository.findById(emp.getDepartmentId())
                    .ifPresent(d -> r.setDepartmentName(d.getName()));
        }
        return r;
    }
}

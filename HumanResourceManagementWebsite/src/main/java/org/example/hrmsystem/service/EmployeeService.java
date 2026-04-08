package org.example.hrmsystem.service;

import org.example.hrmsystem.dto.EmployeeRequest;
import org.example.hrmsystem.dto.EmployeeResponse;
import org.example.hrmsystem.exception.DuplicateResourceException;
import org.example.hrmsystem.exception.ResourceNotFoundException;
import org.example.hrmsystem.model.*;
import org.example.hrmsystem.repository.DepartmentRepository;
import org.example.hrmsystem.repository.EmployeeRepository;
import org.example.hrmsystem.repository.UserAccountRepository;
import org.example.hrmsystem.security.AppUserDetails;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
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
    private final ManagerEmployeeScopeService managerEmployeeScopeService;
    private final PasswordEncoder passwordEncoder;

    public EmployeeService(EmployeeRepository employeeRepository,
                           DepartmentRepository departmentRepository,
                           UserAccountRepository userAccountRepository,
                           ManagerEmployeeScopeService managerEmployeeScopeService,
                           PasswordEncoder passwordEncoder) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.userAccountRepository = userAccountRepository;
        this.managerEmployeeScopeService = managerEmployeeScopeService;
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

    /**
     * ADMIN/HR: toàn bộ. MANAGER: chỉ nhân viên thuộc phòng ban trong phạm vi quản lý.
     */
    public Page<EmployeeResponse> findAllForActor(AppUserDetails actor,
                                                  String keyword,
                                                  String statusStr,
                                                  Long departmentId,
                                                  Pageable pageable) {
        Role role = Role.valueOf(actor.getRole());
        if (role == Role.ADMIN || role == Role.HR) {
            return findAll(keyword, statusStr, departmentId, pageable);
        }
        if (role == Role.MANAGER) {
            Long actorKey = resolveActorEmployeeKey(actor);
            Set<Long> deptIds = managerEmployeeScopeService.managedDepartmentIds(actorKey);
            if (deptIds.isEmpty()) {
                return Page.empty(pageable);
            }
            if (departmentId != null && !deptIds.contains(departmentId)) {
                throw new AccessDeniedException("Bạn không quản lý phòng ban này");
            }
            EmployeeStatus status = null;
            if (statusStr != null && !statusStr.isBlank()) {
                try { status = EmployeeStatus.valueOf(statusStr.toUpperCase()); }
                catch (IllegalArgumentException ignored) {}
            }
            String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
            return employeeRepository.searchInDepartments(kw, status, departmentId, deptIds, pageable)
                    .map(this::toResponse);
        }
        throw new AccessDeniedException("Không có quyền xem danh sách nhân viên");
    }

    // ── Read One ─────────────────────────────────────────────────────────────

    public EmployeeResponse findById(Long id) {
        Employee emp = getOrThrow(id);
        return toResponse(emp);
    }

    /**
     * EMPLOYEE: chỉ hồ sơ của chính mình (khóa employeeId hoặc userId fallback).
     * MANAGER: nhân viên thuộc phạm vi quản lý.
     * ADMIN / HR: mọi id.
     */
    public EmployeeResponse getByIdForActor(Long id, AppUserDetails actor) {
        Role role = Role.valueOf(actor.getRole());
        if (role == Role.ADMIN || role == Role.HR) {
            return findById(id);
        }
        Long actorKey = resolveActorEmployeeKey(actor);
        if (role == Role.EMPLOYEE) {
            if (!id.equals(actorKey)) {
                throw new AccessDeniedException("Bạn chỉ được xem hồ sơ của chính mình");
            }
            return findById(id);
        }
        if (role == Role.MANAGER) {
            if (!managerEmployeeScopeService.managesEmployee(actorKey, id)) {
                throw new AccessDeniedException("Bạn chỉ được xem nhân viên thuộc phòng ban quản lý");
            }
            return findById(id);
        }
        throw new AccessDeniedException("Không có quyền xem hồ sơ nhân viên");
    }

    /** EMPLOYEE chỉ được cập nhật avatar của chính mình (khóa giống chấm công). */
    public void assertEmployeeAvatarSelfOnly(AppUserDetails actor, Long employeeId) {
        if (Role.valueOf(actor.getRole()) != Role.EMPLOYEE) {
            return;
        }
        if (!employeeId.equals(resolveActorEmployeeKey(actor))) {
            throw new AccessDeniedException("Bạn chỉ được đổi ảnh đại diện của chính mình");
        }
    }

    private static Long resolveActorEmployeeKey(AppUserDetails user) {
        Long eid = user.getEmployeeId();
        return eid != null ? eid : user.getUserId();
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

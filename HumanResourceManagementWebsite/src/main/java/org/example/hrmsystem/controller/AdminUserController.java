package org.example.hrmsystem.controller;

import org.example.hrmsystem.dto.EmployeeRequest;
import org.example.hrmsystem.dto.EmployeeResponse;
import org.example.hrmsystem.exception.DuplicateResourceException;
import org.example.hrmsystem.exception.ResourceNotFoundException;
import org.example.hrmsystem.model.Role;
import org.example.hrmsystem.model.UserAccount;
import org.example.hrmsystem.repository.EmployeeRepository;
import org.example.hrmsystem.repository.UserAccountRepository;
import org.example.hrmsystem.service.EmployeeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private static final String EMAIL_REGEX = "^[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}$";

    private final UserAccountRepository userAccountRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeService employeeService;
    private final PasswordEncoder passwordEncoder;

    public AdminUserController(UserAccountRepository userAccountRepository,
                               EmployeeRepository employeeRepository,
                               EmployeeService employeeService,
                               PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.employeeRepository = employeeRepository;
        this.employeeService = employeeService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * GET /api/admin/users
     * Danh sách tất cả tài khoản (trừ thông tin nhạy cảm).
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listAll() {
        List<Map<String, Object>> result = userAccountRepository.findAll()
                .stream()
                .map(u -> Map.<String, Object>of(
                        "id",         u.getId(),
                        "username",   u.getUsername(),
                        "role",       u.getRole() != null ? u.getRole().name() : "",
                        "employeeId", u.getEmployeeId() != null ? u.getEmployeeId() : "",
                        "isActive",   u.isActive()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/available-employees")
    public ResponseEntity<List<Map<String, Object>>> availableEmployees() {
        Set<Long> usedEmployeeIds = userAccountRepository.findAll().stream()
                .map(UserAccount::getEmployeeId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        List<Map<String, Object>> result = employeeRepository.findAll().stream()
                .filter(e -> e.getId() != null && !usedEmployeeIds.contains(e.getId()))
                .map(e -> Map.<String, Object>of(
                        "id", e.getId(),
                        "employeeCode", e.getEmployeeCode() == null ? "" : e.getEmployeeCode(),
                        "fullName", e.getFullName() == null ? "" : e.getFullName()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/create-employee-account")
    public ResponseEntity<Map<String, Object>> createEmployeeWithAccount(@RequestBody Map<String, Object> body) {
        String fullName = String.valueOf(body.getOrDefault("fullName", "")).trim();
        String email = String.valueOf(body.getOrDefault("email", "")).trim().toLowerCase();
        String employeeCode = String.valueOf(body.getOrDefault("employeeCode", "")).trim();
        String initialPassword = String.valueOf(body.getOrDefault("password", "")).trim();
        String loginUsernameRaw = String.valueOf(body.getOrDefault("loginUsername", "")).trim().toLowerCase();
        String roleRaw = String.valueOf(body.getOrDefault("role", "EMPLOYEE")).trim().toUpperCase();

        if (fullName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "fullName không được để trống."));
        }
        Role selectedRole;
        try {
            selectedRole = Role.valueOf(roleRaw);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", "Role không hợp lệ."));
        }
        if (employeeCode.isBlank()) {
            employeeCode = "EMP-" + System.currentTimeMillis();
        }
        if (employeeRepository.findByEmployeeCode(employeeCode).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Mã nhân viên đã tồn tại."));
        }
        if (!email.isBlank() && !email.matches(EMAIL_REGEX)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email không đúng định dạng."));
        }

        EmployeeRequest req = new EmployeeRequest();
        req.setFullName(fullName);
        req.setEmployeeCode(employeeCode);
        req.setEmail(email.isBlank() ? null : email);
        req.setStatus("ACTIVE");
        if (!initialPassword.isBlank()) {
            req.setInitialPassword(initialPassword);
        }

        EmployeeResponse created = employeeService.create(req);
        UserAccount createdAccount = userAccountRepository.findByEmployeeId(created.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản vừa tạo cho nhân viên: id=" + created.getId()));
        createdAccount.setRole(selectedRole);
        if (!loginUsernameRaw.isBlank()
                && !loginUsernameRaw.equals(createdAccount.getUsername())) {
            if (userAccountRepository.existsByUsername(loginUsernameRaw)) {
                throw new DuplicateResourceException("Username đăng nhập đã tồn tại: " + loginUsernameRaw);
            }
            createdAccount.setUsername(loginUsernameRaw);
        }
        createdAccount.touchUpdatedAt();
        UserAccount saved = userAccountRepository.save(createdAccount);

        return ResponseEntity.ok(Map.of(
                "message", "Tạo nhân viên và tài khoản thành công",
                "employeeId", created.getId(),
                "employeeCode", created.getEmployeeCode(),
                "username", saved.getUsername(),
                "role", selectedRole.name()
        ));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createUser(@RequestBody Map<String, Object> body) {
        String username = String.valueOf(body.getOrDefault("username", "")).trim().toLowerCase();
        String rawPassword = String.valueOf(body.getOrDefault("password", "")).trim();
        String roleRaw = String.valueOf(body.getOrDefault("role", "EMPLOYEE")).trim().toUpperCase();

        if (username.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Username không được để trống."));
        }
        if (rawPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("message", "Mật khẩu phải có ít nhất 6 ký tự."));
        }
        if (userAccountRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Username đã tồn tại: " + username);
        }

        org.example.hrmsystem.model.Role role;
        try {
            role = org.example.hrmsystem.model.Role.valueOf(roleRaw);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", "Role không hợp lệ."));
        }

        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setPassword(passwordEncoder.encode(rawPassword));
        account.setRole(role);
        account.setActive(true);

        Object employeeIdObj = body.get("employeeId");
        String employeeInput = employeeIdObj == null ? "" : String.valueOf(employeeIdObj).trim();
        Long employeeId = null;
        if (!employeeInput.isBlank()) {
            try {
                employeeId = Long.parseLong(employeeInput);
                if (!employeeRepository.existsById(employeeId)) {
                    return ResponseEntity.badRequest().body(Map.of("message", "employeeId không tồn tại trong bảng nhân viên."));
                }
            } catch (NumberFormatException ex) {
                employeeId = employeeRepository.findByEmployeeCode(employeeInput)
                        .map(org.example.hrmsystem.model.Employee::getId)
                        .orElse(null);
                if (employeeId == null) {
                    return ResponseEntity.badRequest().body(Map.of("message", "employeeId/mã nhân viên không tồn tại."));
                }
            }
        }
        if (role == org.example.hrmsystem.model.Role.EMPLOYEE && employeeId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Tài khoản EMPLOYEE bắt buộc phải có employeeId hoặc mã nhân viên (chọn nhân viên chưa có tài khoản)."));
        }
        if (employeeId != null) {
            if (userAccountRepository.findByEmployeeId(employeeId).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("message", "employeeId đã được gắn với tài khoản khác."));
            }
            account.setEmployeeId(employeeId);
        }

        account.touchUpdatedAt();
        UserAccount saved = userAccountRepository.save(account);
        return ResponseEntity.ok(Map.of(
                "id", saved.getId(),
                "username", saved.getUsername(),
                "role", saved.getRole().name(),
                "employeeId", saved.getEmployeeId() == null ? "" : saved.getEmployeeId(),
                "isActive", saved.isActive()
        ));
    }

    @PostMapping("/{id}/lock")
    public ResponseEntity<Map<String, String>> lockUser(@PathVariable Long id) {
        UserAccount account = userAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản không tồn tại: id=" + id));
        account.setActive(false);
        account.touchUpdatedAt();
        userAccountRepository.save(account);
        return ResponseEntity.ok(Map.of("message", "Khóa tài khoản thành công!"));
    }

    @PostMapping("/{id}/unlock")
    public ResponseEntity<Map<String, String>> unlockUser(@PathVariable Long id) {
        UserAccount account = userAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản không tồn tại: id=" + id));
        account.setActive(true);
        account.touchUpdatedAt();
        userAccountRepository.save(account);
        return ResponseEntity.ok(Map.of("message", "Mở khóa tài khoản thành công!"));
    }

    /**
     * PUT /api/admin/users/{id}/password
     * Body: { "newPassword": "..." }
     * Admin đặt lại mật khẩu cho tài khoản bất kỳ.
     */
    @PutMapping("/{id}/password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.length() < 6) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Mật khẩu mới phải có ít nhất 6 ký tự."));
        }

        UserAccount account = userAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản không tồn tại: id=" + id));

        account.setPassword(passwordEncoder.encode(newPassword));
        account.touchUpdatedAt();
        userAccountRepository.save(account);

        return ResponseEntity.ok(Map.of("message", "Đặt lại mật khẩu thành công!"));
    }

    /**
     * PUT /api/admin/users/{id}/username
     * Body: { "newUsername": "..." }
     * Admin thay đổi username (email đăng nhập) của tài khoản.
     */
    @PutMapping("/{id}/username")
    public ResponseEntity<Map<String, String>> changeUsername(
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        String newUsername = body.get("newUsername");
        if (newUsername == null || newUsername.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Username không được để trống."));
        }
        newUsername = newUsername.trim().toLowerCase();

        UserAccount account = userAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản không tồn tại: id=" + id));

        if (!account.getUsername().equals(newUsername)
                && userAccountRepository.existsByUsername(newUsername)) {
            throw new DuplicateResourceException("Username đã tồn tại: " + newUsername);
        }

        account.setUsername(newUsername);
        account.touchUpdatedAt();
        userAccountRepository.save(account);

        return ResponseEntity.ok(Map.of(
                "message", "Đổi username thành công!",
                "newUsername", newUsername
        ));
    }

    /**
     * PUT /api/admin/users/{id}/role
     * Body: { "newRole": "MANAGER" }
     * Admin thay đổi role của tài khoản (ADMIN, HR, MANAGER, EMPLOYEE).
     */
    @PutMapping("/{id}/role")
    public ResponseEntity<Map<String, String>> changeRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        String newRoleStr = body.get("newRole");
        if (newRoleStr == null || newRoleStr.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Vui lòng cung cấp newRole (ADMIN, HR, MANAGER, EMPLOYEE)."));
        }

        org.example.hrmsystem.model.Role newRole;
        try {
            newRole = org.example.hrmsystem.model.Role.valueOf(newRoleStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Role không hợp lệ: " + newRoleStr + ". Giá trị hợp lệ: ADMIN, HR, MANAGER, EMPLOYEE"));
        }

        UserAccount account = userAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản không tồn tại: id=" + id));

        account.setRole(newRole);
        account.touchUpdatedAt();
        userAccountRepository.save(account);

        return ResponseEntity.ok(Map.of(
                "message", "Đổi role thành công!",
                "newRole", newRole.name()
        ));
    }
}

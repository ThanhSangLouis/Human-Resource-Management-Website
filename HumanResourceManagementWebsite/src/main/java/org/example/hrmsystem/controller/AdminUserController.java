package org.example.hrmsystem.controller;

import org.example.hrmsystem.exception.DuplicateResourceException;
import org.example.hrmsystem.exception.ResourceNotFoundException;
import org.example.hrmsystem.model.UserAccount;
import org.example.hrmsystem.repository.UserAccountRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserController(UserAccountRepository userAccountRepository,
                               PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
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
        userAccountRepository.save(account);

        return ResponseEntity.ok(Map.of(
                "message", "Đổi role thành công!",
                "newRole", newRole.name()
        ));
    }
}

package org.example.hrmsystem.controller;

import java.util.Map;
import org.example.hrmsystem.dto.EmployeeResponse;
import org.example.hrmsystem.model.UserAccount;
import org.example.hrmsystem.repository.UserAccountRepository;
import org.example.hrmsystem.security.AppUserDetails;
import org.example.hrmsystem.service.EmployeeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final UserAccountRepository userAccountRepository;
    private final EmployeeService employeeService;
    private final PasswordEncoder passwordEncoder;

    public ProfileController(UserAccountRepository userAccountRepository,
                             EmployeeService employeeService,
                             PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.employeeService = employeeService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * GET /api/profile/me
     * Trả về thông tin nhân viên gắn với tài khoản đang đăng nhập.
     */
    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile(Authentication authentication) {
        AppUserDetails userDetails = (AppUserDetails) authentication.getPrincipal();
        Long employeeId = userDetails.getEmployeeId();
        if (employeeId == null) {
            return ResponseEntity.ok(Map.of(
                    "username", userDetails.getUsername(),
                    "role", userDetails.getRole(),
                    "employeeId", (Object) null
            ));
        }
        EmployeeResponse emp = employeeService.findById(employeeId);
        return ResponseEntity.ok(emp);
    }

    /**
     * POST /api/profile/change-password
     * Body: { "currentPassword": "...", "newPassword": "..." }
     */
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @RequestBody Map<String, String> body,
            Authentication authentication
    ) {
        String currentPassword = body.get("currentPassword");
        String newPassword     = body.get("newPassword");

        if (currentPassword == null || currentPassword.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Vui lòng nhập mật khẩu hiện tại."));
        }
        if (newPassword == null || newPassword.length() < 6) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Mật khẩu mới phải có ít nhất 6 ký tự."));
        }

        String username = authentication.getName();
        UserAccount account = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản"));

        if (!passwordEncoder.matches(currentPassword, account.getPassword())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Mật khẩu hiện tại không đúng."));
        }

        account.setPassword(passwordEncoder.encode(newPassword));
        userAccountRepository.save(account);

        return ResponseEntity.ok(Map.of("message", "Đổi mật khẩu thành công!"));
    }
}

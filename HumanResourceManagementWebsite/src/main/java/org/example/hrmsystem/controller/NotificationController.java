package org.example.hrmsystem.controller;

import org.example.hrmsystem.dto.NotificationResponse;
import org.example.hrmsystem.security.AppUserDetails;
import org.example.hrmsystem.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Danh sách thông báo in-app của nhân viên đang đăng nhập.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<NotificationResponse>> list(
            @AuthenticationPrincipal AppUserDetails user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long employeeId = user.getEmployeeId();
        if (employeeId == null) {
            return ResponseEntity.ok(Page.<NotificationResponse>empty());
        }
        return ResponseEntity.ok(notificationService.listForEmployee(
                employeeId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        ));
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markRead(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserDetails user
    ) {
        Long employeeId = user.getEmployeeId();
        if (employeeId == null) {
            return ResponseEntity.badRequest().build();
        }
        notificationService.markRead(id, employeeId);
        return ResponseEntity.noContent().build();
    }
}

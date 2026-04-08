package org.example.hrmsystem.controller;

import org.example.hrmsystem.dto.DashboardStatsResponse;
import org.example.hrmsystem.service.DashboardService;
import org.example.hrmsystem.security.AppUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * GET /api/dashboard?month=2026-03
     * ADMIN/HR: toàn hệ thống. MANAGER: phạm vi phòng ban quản lý. EMPLOYEE: 403 (filter chain + PreAuthorize).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR','MANAGER')")
    public ResponseEntity<DashboardStatsResponse> stats(
            @RequestParam(required = false) String month,
            @AuthenticationPrincipal AppUserDetails user
    ) {
        return ResponseEntity.ok(dashboardService.getStats(month, user));
    }
}

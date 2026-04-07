package org.example.hrmsystem.controller;

import org.example.hrmsystem.dto.DashboardStatsResponse;
import org.example.hrmsystem.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
     * Mọi role đã đăng nhập (kể cả EMPLOYEE) — trang Tổng quan sau login.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR','MANAGER','EMPLOYEE')")
    public ResponseEntity<DashboardStatsResponse> stats(
            @RequestParam(required = false) String month
    ) {
        return ResponseEntity.ok(dashboardService.getStats(month));
    }
}

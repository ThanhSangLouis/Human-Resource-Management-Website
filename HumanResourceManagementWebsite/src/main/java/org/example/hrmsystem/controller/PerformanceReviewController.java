package org.example.hrmsystem.controller;

import jakarta.validation.Valid;
import org.example.hrmsystem.dto.PerformanceReviewCreateRequest;
import org.example.hrmsystem.dto.PerformanceReviewResponse;
import org.example.hrmsystem.dto.PerformanceReviewUpdateRequest;
import org.example.hrmsystem.model.Role;
import org.example.hrmsystem.security.AppUserDetails;
import org.example.hrmsystem.service.PerformanceReviewService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/performance-reviews")
public class PerformanceReviewController {

    private final PerformanceReviewService performanceReviewService;

    public PerformanceReviewController(PerformanceReviewService performanceReviewService) {
        this.performanceReviewService = performanceReviewService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR','MANAGER')")
    public ResponseEntity<Page<PerformanceReviewResponse>> list(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<PerformanceReviewResponse> result = performanceReviewService.list(
                year,
                quarter,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "reviewYear", "reviewQuarter", "id"))
        );
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR','MANAGER')")
    public ResponseEntity<PerformanceReviewResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(performanceReviewService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR','MANAGER')")
    public ResponseEntity<PerformanceReviewResponse> create(
            @Valid @RequestBody PerformanceReviewCreateRequest request,
            @AuthenticationPrincipal AppUserDetails user
    ) {
        Role role = Role.valueOf(user.getRole());
        Long actorKey = resolveActorEmployeeKey(user);
        PerformanceReviewResponse r = performanceReviewService.create(
                request,
                user.getUserId(),
                role,
                actorKey
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(r);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR','MANAGER')")
    public ResponseEntity<PerformanceReviewResponse> updateDraft(
            @PathVariable Long id,
            @Valid @RequestBody PerformanceReviewUpdateRequest request,
            @AuthenticationPrincipal AppUserDetails user
    ) {
        Role role = Role.valueOf(user.getRole());
        Long actorKey = resolveActorEmployeeKey(user);
        return ResponseEntity.ok(performanceReviewService.updateDraft(id, request, role, actorKey));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('ADMIN','HR','MANAGER')")
    public ResponseEntity<PerformanceReviewResponse> submit(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserDetails user
    ) {
        Role role = Role.valueOf(user.getRole());
        Long actorKey = resolveActorEmployeeKey(user);
        return ResponseEntity.ok(performanceReviewService.submit(id, role, actorKey));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<PerformanceReviewResponse> approve(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserDetails user
    ) {
        Role role = Role.valueOf(user.getRole());
        Long actorKey = resolveActorEmployeeKey(user);
        return ResponseEntity.ok(performanceReviewService.approve(id, role, actorKey));
    }

    private Long resolveActorEmployeeKey(AppUserDetails userDetails) {
        Long employeeId = userDetails.getEmployeeId();
        return employeeId != null ? employeeId : userDetails.getUserId();
    }
}

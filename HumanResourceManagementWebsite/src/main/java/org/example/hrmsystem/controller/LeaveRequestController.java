package org.example.hrmsystem.controller;

import jakarta.validation.Valid;
import org.example.hrmsystem.dto.LeaveRequestCreateDto;
import org.example.hrmsystem.dto.LeaveRequestResponse;
import org.example.hrmsystem.security.AppUserDetails;
import org.example.hrmsystem.service.LeaveRequestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeParseException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

@RestController
@RequestMapping("/api/leave")
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;

    public LeaveRequestController(LeaveRequestService leaveRequestService) {
        this.leaveRequestService = leaveRequestService;
    }

    @PostMapping
    public ResponseEntity<?> create(
            @AuthenticationPrincipal AppUserDetails userDetails,
            @Valid @RequestBody LeaveRequestCreateDto dto
    ) {
        try {
            Long employeeId = resolveEmployeeId(userDetails);
            LeaveRequestResponse response = leaveRequestService.create(employeeId, dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/my")
    public ResponseEntity<Map<String, Object>> myLeaves(
            @AuthenticationPrincipal AppUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Long employeeId = resolveEmployeeId(userDetails);
        return ResponseEntity.ok(leaveRequestService.getMyLeaves(employeeId, page, size));
    }

    /**
     * Đơn nghỉ APPROVED giao với tháng/ngày — phạm vi theo role (giống lịch sử chấm công).
     * {@code departmentId}: chỉ ADMIN/HR.
     */
    @GetMapping("/approved")
    public ResponseEntity<?> listApproved(
            @AuthenticationPrincipal AppUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) Long departmentId
    ) {
        if (date != null && !date.isBlank()) {
            try {
                LocalDate.parse(date.trim());
            } catch (DateTimeParseException ex) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid date (use yyyy-MM-dd)"));
            }
        }
        if (month != null && !month.isBlank()) {
            try {
                YearMonth.parse(month.trim());
            } catch (DateTimeParseException ex) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid month (use yyyy-MM)"));
            }
        }
        try {
            return ResponseEntity.ok(leaveRequestService.listApprovedLeaves(
                    userDetails, page, size, month, date, departmentId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN','HR','MANAGER')")
    public ResponseEntity<Map<String, Object>> pending(
            @AuthenticationPrincipal AppUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(leaveRequestService.listPending(userDetails, page, size));
    }

    @RequestMapping(value = "/{id}/approve", method = {RequestMethod.PATCH, RequestMethod.POST})
    @PreAuthorize("hasAnyRole('ADMIN','HR','MANAGER')")
    public ResponseEntity<?> approve(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserDetails userDetails
    ) {
        try {
            LeaveRequestResponse response = leaveRequestService.approve(id, userDetails);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @RequestMapping(value = "/{id}/reject", method = {RequestMethod.PATCH, RequestMethod.POST})
    @PreAuthorize("hasAnyRole('ADMIN','HR','MANAGER')")
    public ResponseEntity<?> reject(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserDetails userDetails
    ) {
        try {
            LeaveRequestResponse response = leaveRequestService.reject(id, userDetails);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    private Long resolveEmployeeId(AppUserDetails userDetails) {
        Long employeeId = userDetails.getEmployeeId();
        if (employeeId == null) {
            employeeId = userDetails.getUserId();
        }
        return employeeId;
    }
}

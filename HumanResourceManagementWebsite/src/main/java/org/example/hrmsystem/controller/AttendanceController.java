package org.example.hrmsystem.controller;

import org.example.hrmsystem.dto.AttendanceResponse;
import org.example.hrmsystem.model.Role;
import org.example.hrmsystem.security.AppUserDetails;
import org.example.hrmsystem.service.AttendanceHistoryAccess;
import org.example.hrmsystem.service.AttendanceService;
import org.example.hrmsystem.service.ExcelExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.example.hrmsystem.model.AttendanceStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Map;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final ExcelExportService excelExportService;

    public AttendanceController(AttendanceService attendanceService, ExcelExportService excelExportService) {
        this.attendanceService = attendanceService;
        this.excelExportService = excelExportService;
    }

    /**
     * US18 — Xuất Excel chấm công theo tháng (HR/Admin).
     */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam String month,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String status
    ) {
        if (month == null || month.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            YearMonth.parse(month.trim());
        } catch (DateTimeParseException ex) {
            return ResponseEntity.badRequest().build();
        }
        if (status != null && !status.isBlank()) {
            try {
                AttendanceStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.badRequest().build();
            }
        }
        try {
            byte[] data = excelExportService.exportAttendanceMonth(month, departmentId, status);
            String filename = "chamcong_" + month.replace("-", "") + ".xlsx";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
            headers.setContentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            return ResponseEntity.ok().headers(headers).body(data);
        } catch (IOException ex) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/check-in")
    public ResponseEntity<?> checkIn(
            @AuthenticationPrincipal AppUserDetails userDetails,
            @RequestParam(required = false) String note
    ) {
        try {
            Long employeeId = resolveEmployeeId(userDetails);
            AttendanceResponse response = attendanceService.checkIn(employeeId, note);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/check-out")
    public ResponseEntity<?> checkOut(
            @AuthenticationPrincipal AppUserDetails userDetails,
            @RequestParam(required = false) String note
    ) {
        try {
            Long employeeId = resolveEmployeeId(userDetails);
            AttendanceResponse response = attendanceService.checkOut(employeeId, note);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/today")
    public ResponseEntity<AttendanceResponse> todayStatus(
            @AuthenticationPrincipal AppUserDetails userDetails
    ) {
        Long employeeId = resolveEmployeeId(userDetails);
        return ResponseEntity.ok(attendanceService.getTodayStatus(employeeId));
    }

    @GetMapping("/history")
    public ResponseEntity<?> history(
            @AuthenticationPrincipal AppUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String status
    ) {
        AttendanceStatus st = null;
        if (status != null && !status.isBlank()) {
            try {
                st = AttendanceStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid status filter"));
            }
        }
        if (date != null && !date.isBlank()) {
            try {
                java.time.LocalDate.parse(date.trim());
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
        AttendanceHistoryAccess access = switch (Role.valueOf(userDetails.getRole())) {
            case EMPLOYEE -> AttendanceHistoryAccess.OWN_EMPLOYEE;
            case MANAGER -> AttendanceHistoryAccess.MANAGED_DEPARTMENTS;
            case ADMIN, HR -> AttendanceHistoryAccess.ALL;
        };
        Long actorEmployeeId = resolveEmployeeId(userDetails);
        return ResponseEntity.ok(attendanceService.getHistory(
                access, actorEmployeeId, month, date, departmentId, st, page, size));
    }

    private Long resolveEmployeeId(AppUserDetails userDetails) {
        Long employeeId = userDetails.getEmployeeId();
        if (employeeId == null) {
            // Fallback: use userId as employeeId for admin/hr accounts without employee profile
            employeeId = userDetails.getUserId();
        }
        return employeeId;
    }
}

package org.example.hrmsystem.controller;

import jakarta.validation.Valid;
import org.example.hrmsystem.dto.PayrollGenerateRequest;
import org.example.hrmsystem.dto.PayrollGenerateResult;
import org.example.hrmsystem.dto.SalaryHistoryResponse;
import org.example.hrmsystem.service.ExcelExportService;
import org.example.hrmsystem.service.PayrollService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/payroll")
public class PayrollController {

    private final PayrollService payrollService;
    private final ExcelExportService excelExportService;

    public PayrollController(PayrollService payrollService, ExcelExportService excelExportService) {
        this.payrollService = payrollService;
        this.excelExportService = excelExportService;
    }

    /**
     * GET /api/payroll?month=2025-03&page=0&size=20
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<Page<SalaryHistoryResponse>> list(
            @RequestParam String month,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<SalaryHistoryResponse> result = payrollService.listByMonth(
                month,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))
        );
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/payroll/generate — sinh bảng lương cho mọi nhân viên ACTIVE (không ghi đè tháng đã có).
     */
    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<PayrollGenerateResult> generate(@Valid @RequestBody PayrollGenerateRequest request) {
        return ResponseEntity.ok(payrollService.generate(request));
    }

    /**
     * GET /api/payroll/export?month=2025-03
     */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<byte[]> export(@RequestParam String month) {
        try {
            byte[] data = excelExportService.exportPayrollMonth(month);
            String filename = "bangluong_" + month.replace("-", "") + ".xlsx";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
            headers.setContentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            return ResponseEntity.ok().headers(headers).body(data);
        } catch (IOException ex) {
            return ResponseEntity.internalServerError().build();
        }
    }
}

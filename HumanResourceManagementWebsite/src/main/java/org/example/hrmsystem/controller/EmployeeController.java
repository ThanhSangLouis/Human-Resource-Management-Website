package org.example.hrmsystem.controller;

import jakarta.validation.Valid;
import org.example.hrmsystem.dto.EmployeeRequest;
import org.example.hrmsystem.dto.EmployeeResponse;
import org.example.hrmsystem.service.AvatarService;
import org.example.hrmsystem.service.EmployeeService;
import org.example.hrmsystem.service.ExcelExportService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final AvatarService avatarService;
    private final ExcelExportService excelExportService;

    public EmployeeController(EmployeeService employeeService,
                              AvatarService avatarService,
                              ExcelExportService excelExportService) {
        this.employeeService = employeeService;
        this.avatarService = avatarService;
        this.excelExportService = excelExportService;
    }

    /**
     * GET /api/employees?keyword=&status=&departmentId=&page=0&size=10&sort=fullName,asc
     * Roles: ADMIN, HR, MANAGER
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR','MANAGER')")
    public ResponseEntity<Page<EmployeeResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "fullName,asc") String sort
    ) {
        String[] parts = sort.split(",");
        Sort.Direction dir = parts.length > 1 && parts[1].equalsIgnoreCase("desc")
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(dir, parts[0]));
        return ResponseEntity.ok(employeeService.findAll(keyword, status, departmentId, pageable));
    }

    /**
     * GET /api/employees/export?keyword=&status=&departmentId=
     * Phải khai báo trước /{id} để không bị ánh xạ "export" thành id.
     * Roles: ADMIN, HR
     */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long departmentId
    ) {
        try {
            byte[] data = excelExportService.exportEmployees(keyword, status, departmentId);
            String filename = "nhanvien_" +
                    LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".xlsx";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(
                    ContentDisposition.attachment().filename(filename).build());
            headers.setContentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            return ResponseEntity.ok().headers(headers).body(data);
        } catch (IOException ex) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GET /api/employees/{id}
     * Roles: ADMIN, HR, MANAGER, EMPLOYEE
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EmployeeResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.findById(id));
    }

    /**
     * POST /api/employees
     * Roles: ADMIN, HR
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<EmployeeResponse> create(@Valid @RequestBody EmployeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeeService.create(request));
    }

    /**
     * PUT /api/employees/{id}
     * Roles: ADMIN, HR
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<EmployeeResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody EmployeeRequest request
    ) {
        return ResponseEntity.ok(employeeService.update(id, request));
    }

    /**
     * DELETE /api/employees/{id}  → soft delete (status = RESIGNED)
     * Roles: ADMIN, HR
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        employeeService.delete(id);
        return ResponseEntity.ok(Map.of("message", "Nhân viên đã được đánh dấu nghỉ việc"));
    }

    /**
     * POST /api/employees/{id}/avatar
     * Roles: ADMIN, HR, EMPLOYEE (bản thân)
     */
    @PostMapping(value = "/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','HR','EMPLOYEE')")
    public ResponseEntity<Map<String, String>> uploadAvatar(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file
    ) {
        try {
            String avatarUrl = avatarService.uploadAvatar(id, file);
            return ResponseEntity.ok(Map.of("avatarUrl", avatarUrl));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (IOException ex) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Lỗi lưu file: " + ex.getMessage()));
        }
    }
}

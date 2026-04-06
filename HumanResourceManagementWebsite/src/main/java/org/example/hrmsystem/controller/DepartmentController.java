package org.example.hrmsystem.controller;

import jakarta.validation.Valid;
import org.example.hrmsystem.dto.DepartmentRequest;
import org.example.hrmsystem.dto.DepartmentResponse;
import org.example.hrmsystem.dto.EmployeeResponse;
import org.example.hrmsystem.service.DepartmentService;
import org.example.hrmsystem.service.EmployeeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService departmentService;
    private final EmployeeService employeeService;

    public DepartmentController(DepartmentService departmentService, EmployeeService employeeService) {
        this.departmentService = departmentService;
        this.employeeService = employeeService;
    }

    /**
     * GET /api/departments?keyword=&page=0&size=10&sort=name,asc
     * Roles: ADMIN, HR, MANAGER
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR','MANAGER')")
    public ResponseEntity<Page<DepartmentResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name,asc") String sort
    ) {
        String[] sortParts = sort.split(",");
        Sort.Direction dir = sortParts.length > 1 && sortParts[1].equalsIgnoreCase("desc")
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(dir, sortParts[0]));
        return ResponseEntity.ok(departmentService.findAll(keyword, pageable));
    }

    /**
     * GET /api/departments/{id}
     * Roles: ADMIN, HR, MANAGER
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR','MANAGER')")
    public ResponseEntity<DepartmentResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(departmentService.findById(id));
    }

    /**
     * GET /api/departments/{id}/employees — danh sách nhân viên thuộc phòng (mặc định chỉ ACTIVE).
     * Roles: ADMIN, HR, MANAGER
     */
    @GetMapping("/{id}/employees")
    @PreAuthorize("hasAnyRole('ADMIN','HR','MANAGER')")
    public ResponseEntity<Page<EmployeeResponse>> listEmployeesByDepartment(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "fullName,asc") String sort
    ) {
        departmentService.findById(id);
        String[] sortParts = sort.split(",");
        Sort.Direction dir = sortParts.length > 1 && sortParts[1].equalsIgnoreCase("desc")
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(dir, sortParts[0]));
        return ResponseEntity.ok(employeeService.findAll(null, "ACTIVE", id, pageable));
    }

    /**
     * POST /api/departments
     * Roles: ADMIN, HR
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<DepartmentResponse> create(@Valid @RequestBody DepartmentRequest request) {
        DepartmentResponse created = departmentService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * PUT /api/departments/{id}
     * Roles: ADMIN, HR
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<DepartmentResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody DepartmentRequest request
    ) {
        return ResponseEntity.ok(departmentService.update(id, request));
    }

    /**
     * DELETE /api/departments/{id}
     * Roles: ADMIN, HR
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        departmentService.delete(id);
        return ResponseEntity.ok(Map.of("message", "Xóa phòng ban thành công"));
    }
}

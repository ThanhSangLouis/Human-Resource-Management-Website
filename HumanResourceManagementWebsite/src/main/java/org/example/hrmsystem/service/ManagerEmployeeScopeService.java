package org.example.hrmsystem.service;

import org.example.hrmsystem.model.Department;
import org.example.hrmsystem.model.Employee;
import org.example.hrmsystem.repository.DepartmentRepository;
import org.example.hrmsystem.repository.EmployeeRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Xác định nhân viên thuộc phạm vi quản lý của trưởng phòng (đồng bộ với lịch sử chấm công).
 */
@Service
public class ManagerEmployeeScopeService {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;

    public ManagerEmployeeScopeService(
            DepartmentRepository departmentRepository,
            EmployeeRepository employeeRepository
    ) {
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
    }

    public Set<Long> visibleEmployeeIdsForManager(Long managerEmployeeId) {
        if (managerEmployeeId == null) {
            return Set.of();
        }
        Set<Long> deptIds = new HashSet<>();
        for (Department d : departmentRepository.findByManagerId(managerEmployeeId)) {
            deptIds.add(d.getId());
        }
        if (deptIds.isEmpty()) {
            employeeRepository.findById(managerEmployeeId)
                    .map(Employee::getDepartmentId)
                    .filter(Objects::nonNull)
                    .ifPresent(deptIds::add);
        }
        if (deptIds.isEmpty()) {
            return Set.of();
        }
        return employeeRepository.findByDepartmentIdIn(deptIds).stream()
                .map(Employee::getId)
                .collect(Collectors.toSet());
    }

    public boolean managesEmployee(Long managerEmployeeKey, Long employeeId) {
        if (employeeId == null) {
            return false;
        }
        return visibleEmployeeIdsForManager(managerEmployeeKey).contains(employeeId);
    }
}

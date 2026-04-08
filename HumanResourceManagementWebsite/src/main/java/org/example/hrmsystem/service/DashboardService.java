package org.example.hrmsystem.service;

import org.example.hrmsystem.dto.AttendanceMonthStats;
import org.example.hrmsystem.dto.DashboardStatsResponse;
import org.example.hrmsystem.dto.DepartmentStatRow;
import org.example.hrmsystem.model.AttendanceStatus;
import org.example.hrmsystem.model.Department;
import org.example.hrmsystem.model.EmployeeStatus;
import org.example.hrmsystem.model.Role;
import org.example.hrmsystem.repository.AttendanceRepository;
import org.example.hrmsystem.repository.DepartmentRepository;
import org.example.hrmsystem.repository.EmployeeRepository;
import org.example.hrmsystem.security.AppUserDetails;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final ManagerEmployeeScopeService managerEmployeeScopeService;

    public DashboardService(
            EmployeeRepository employeeRepository,
            DepartmentRepository departmentRepository,
            AttendanceRepository attendanceRepository,
            ManagerEmployeeScopeService managerEmployeeScopeService
    ) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.attendanceRepository = attendanceRepository;
        this.managerEmployeeScopeService = managerEmployeeScopeService;
    }

    public DashboardStatsResponse getStats(String monthParam, AppUserDetails actor) {
        Role role = Role.valueOf(actor.getRole());
        if (role == Role.ADMIN || role == Role.HR) {
            return buildGlobalStats(monthParam);
        }
        if (role == Role.MANAGER) {
            Long key = actor.getEmployeeId() != null ? actor.getEmployeeId() : actor.getUserId();
            Set<Long> empIds = managerEmployeeScopeService.visibleEmployeeIdsForManager(key);
            Set<Long> deptIds = managerEmployeeScopeService.managedDepartmentIds(key);
            return buildScopedStats(monthParam, empIds, deptIds);
        }
        throw new AccessDeniedException("Không có quyền xem dashboard");
    }

    private DashboardStatsResponse buildGlobalStats(String monthParam) {
        DashboardStatsResponse resp = new DashboardStatsResponse();

        resp.setTotalEmployees(employeeRepository.count());

        Map<String, Long> statusMap = new LinkedHashMap<>();
        for (EmployeeStatus s : EmployeeStatus.values()) {
            statusMap.put(s.name(), employeeRepository.countByStatus(s));
        }
        resp.setByStatus(statusMap);

        resp.setTotalDepartments(departmentRepository.count());

        Map<Long, Department> deptById = departmentRepository.findAll()
                .stream()
                .collect(Collectors.toMap(Department::getId, d -> d));

        List<DepartmentStatRow> byDept = employeeRepository.countGroupByDepartment()
                .stream()
                .map(row -> {
                    Long deptId = (Long) row[0];
                    long cnt = (Long) row[1];
                    String name = deptById.containsKey(deptId)
                            ? deptById.get(deptId).getName()
                            : "Phòng ban #" + deptId;
                    return new DepartmentStatRow(deptId, name, cnt);
                })
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .collect(Collectors.toList());
        resp.setByDepartment(byDept);

        YearMonth ym = parseYearMonth(monthParam);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        long totalRecords = attendanceRepository.countInRange(from, to);
        Map<String, Long> attByStatus = attendanceStatusCountsGlobal(from, to);
        resp.setAttendanceMonth(buildAttendanceMonth(ym, totalRecords, attByStatus));

        resp.setGeneratedAt(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
        return resp;
    }

    private DashboardStatsResponse buildScopedStats(String monthParam, Set<Long> empIds, Set<Long> deptIds) {
        DashboardStatsResponse resp = new DashboardStatsResponse();

        if (empIds == null || empIds.isEmpty()) {
            resp.setTotalEmployees(0L);
            Map<String, Long> emptyStatus = new LinkedHashMap<>();
            for (EmployeeStatus s : EmployeeStatus.values()) {
                emptyStatus.put(s.name(), 0L);
            }
            resp.setByStatus(emptyStatus);
            resp.setTotalDepartments((long) (deptIds != null ? deptIds.size() : 0));
            resp.setByDepartment(List.of());
            YearMonth ym = parseYearMonth(monthParam);
            Map<String, Long> attEmpty = new LinkedHashMap<>();
            for (AttendanceStatus s : AttendanceStatus.values()) {
                attEmpty.put(s.name(), 0L);
            }
            resp.setAttendanceMonth(new AttendanceMonthStats(ym.toString(), 0L, attEmpty, 0.0));
            resp.setGeneratedAt(
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            );
            return resp;
        }

        resp.setTotalEmployees(employeeRepository.countByIdIn(empIds));

        Map<String, Long> statusMap = new LinkedHashMap<>();
        for (EmployeeStatus s : EmployeeStatus.values()) {
            statusMap.put(s.name(), 0L);
        }
        for (Object[] row : employeeRepository.countByStatusGroupedForIds(empIds)) {
            EmployeeStatus st = (EmployeeStatus) row[0];
            long cnt = (Long) row[1];
            statusMap.put(st.name(), cnt);
        }
        resp.setByStatus(statusMap);

        resp.setTotalDepartments((long) (deptIds != null ? deptIds.size() : 0));

        Map<Long, Department> deptById = departmentRepository.findAllById(deptIds).stream()
                .collect(Collectors.toMap(Department::getId, d -> d));

        List<DepartmentStatRow> byDept = new ArrayList<>();
        for (Long did : deptIds) {
            long cnt = employeeRepository.countByDepartmentIdAndIdIn(did, empIds);
            if (cnt > 0) {
                String name = deptById.containsKey(did)
                        ? deptById.get(did).getName()
                        : "Phòng ban #" + did;
                byDept.add(new DepartmentStatRow(did, name, cnt));
            }
        }
        byDept.sort((a, b) -> Long.compare(b.getCount(), a.getCount()));
        resp.setByDepartment(byDept);

        YearMonth ym = parseYearMonth(monthParam);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        long totalRecords = attendanceRepository.countInRangeForEmployees(from, to, empIds);
        Map<String, Long> attByStatus = attendanceStatusCountsScoped(from, to, empIds);
        resp.setAttendanceMonth(buildAttendanceMonth(ym, totalRecords, attByStatus));

        resp.setGeneratedAt(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
        return resp;
    }

    private static YearMonth parseYearMonth(String monthParam) {
        return (monthParam == null || monthParam.isBlank())
                ? YearMonth.now()
                : YearMonth.parse(monthParam.trim());
    }

    private Map<String, Long> attendanceStatusCountsGlobal(LocalDate from, LocalDate to) {
        Map<String, Long> attByStatus = new LinkedHashMap<>();
        for (AttendanceStatus s : AttendanceStatus.values()) {
            attByStatus.put(s.name(), 0L);
        }
        attendanceRepository.countByStatusInRange(from, to).forEach(row -> {
            AttendanceStatus st = (AttendanceStatus) row[0];
            long cnt = (Long) row[1];
            attByStatus.put(st.name(), cnt);
        });
        return attByStatus;
    }

    private Map<String, Long> attendanceStatusCountsScoped(LocalDate from, LocalDate to, Set<Long> empIds) {
        Map<String, Long> attByStatus = new LinkedHashMap<>();
        for (AttendanceStatus s : AttendanceStatus.values()) {
            attByStatus.put(s.name(), 0L);
        }
        attendanceRepository.countByStatusInRangeForEmployees(from, to, empIds).forEach(row -> {
            AttendanceStatus st = (AttendanceStatus) row[0];
            long cnt = (Long) row[1];
            attByStatus.put(st.name(), cnt);
        });
        return attByStatus;
    }

    private static AttendanceMonthStats buildAttendanceMonth(
            YearMonth ym,
            long totalRecords,
            Map<String, Long> attByStatus
    ) {
        long present = attByStatus.getOrDefault(AttendanceStatus.PRESENT.name(), 0L);
        long late = attByStatus.getOrDefault(AttendanceStatus.LATE.name(), 0L);
        long halfDay = attByStatus.getOrDefault(AttendanceStatus.HALF_DAY.name(), 0L);
        long onLeave = attByStatus.getOrDefault(AttendanceStatus.ON_LEAVE.name(), 0L);
        double rate = totalRecords > 0
                ? Math.round((present + late + halfDay + onLeave) * 1000.0 / totalRecords) / 10.0
                : 0.0;
        return new AttendanceMonthStats(ym.toString(), totalRecords, attByStatus, rate);
    }
}

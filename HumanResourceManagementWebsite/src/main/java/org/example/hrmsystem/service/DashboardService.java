package org.example.hrmsystem.service;

import org.example.hrmsystem.dto.AttendanceMonthStats;
import org.example.hrmsystem.dto.DashboardStatsResponse;
import org.example.hrmsystem.dto.DepartmentStatRow;
import org.example.hrmsystem.model.AttendanceStatus;
import org.example.hrmsystem.model.Department;
import org.example.hrmsystem.model.EmployeeStatus;
import org.example.hrmsystem.repository.AttendanceRepository;
import org.example.hrmsystem.repository.DepartmentRepository;
import org.example.hrmsystem.repository.EmployeeRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final AttendanceRepository attendanceRepository;

    public DashboardService(
            EmployeeRepository employeeRepository,
            DepartmentRepository departmentRepository,
            AttendanceRepository attendanceRepository
    ) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.attendanceRepository = attendanceRepository;
    }

    public DashboardStatsResponse getStats(String monthParam) {
        DashboardStatsResponse resp = new DashboardStatsResponse();

        // ── 1. Tổng nhân viên ────────────────────────────────────────────────
        resp.setTotalEmployees(employeeRepository.count());

        // ── 2. Theo trạng thái ───────────────────────────────────────────────
        Map<String, Long> statusMap = new LinkedHashMap<>();
        for (EmployeeStatus s : EmployeeStatus.values()) {
            statusMap.put(s.name(), employeeRepository.countByStatus(s));
        }
        resp.setByStatus(statusMap);

        // ── 3. Tổng phòng ban ────────────────────────────────────────────────
        resp.setTotalDepartments(departmentRepository.count());

        // ── 4. Số nhân viên theo phòng ban ───────────────────────────────────
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

        // ── 5. Attendance stats theo tháng ───────────────────────────────────
        YearMonth ym = (monthParam == null || monthParam.isBlank())
                ? YearMonth.now()
                : YearMonth.parse(monthParam.trim());
        LocalDate from = ym.atDay(1);
        LocalDate to   = ym.atEndOfMonth();

        long totalRecords = attendanceRepository.countInRange(from, to);

        Map<String, Long> attByStatus = new LinkedHashMap<>();
        for (AttendanceStatus s : AttendanceStatus.values()) {
            attByStatus.put(s.name(), 0L);
        }
        attendanceRepository.countByStatusInRange(from, to).forEach(row -> {
            AttendanceStatus st = (AttendanceStatus) row[0];
            long cnt = (Long) row[1];
            attByStatus.put(st.name(), cnt);
        });

        // attendanceRate = (PRESENT + LATE + HALF_DAY + ON_LEAVE) / totalRecords * 100
        long present  = attByStatus.getOrDefault(AttendanceStatus.PRESENT.name(), 0L);
        long late     = attByStatus.getOrDefault(AttendanceStatus.LATE.name(), 0L);
        long halfDay  = attByStatus.getOrDefault(AttendanceStatus.HALF_DAY.name(), 0L);
        long onLeave  = attByStatus.getOrDefault(AttendanceStatus.ON_LEAVE.name(), 0L);
        double rate = totalRecords > 0
                ? Math.round((present + late + halfDay + onLeave) * 1000.0 / totalRecords) / 10.0
                : 0.0;

        resp.setAttendanceMonth(new AttendanceMonthStats(ym.toString(), totalRecords, attByStatus, rate));

        // ── 6. Thời điểm generate ─────────────────────────────────────────────
        resp.setGeneratedAt(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );

        return resp;
    }
}

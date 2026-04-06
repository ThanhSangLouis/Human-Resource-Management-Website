package org.example.hrmsystem.service;

import org.example.hrmsystem.model.Attendance;
import org.example.hrmsystem.model.AttendanceStatus;
import org.example.hrmsystem.model.Employee;
import org.example.hrmsystem.model.EmployeeStatus;
import org.example.hrmsystem.model.LeaveRequest;
import org.example.hrmsystem.model.LeaveStatus;
import org.example.hrmsystem.repository.AttendanceRepository;
import org.example.hrmsystem.repository.EmployeeRepository;
import org.example.hrmsystem.repository.LeaveRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Đóng ngày: đồng bộ ON_LEAVE từ đơn đã duyệt, sau đó ghi ABSENT cho NV ACTIVE chưa có bản ghi (trừ Chủ nhật).
 */
@Service
public class AttendanceClosingService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceClosingService.class);

    private final LeaveRequestRepository leaveRequestRepository;
    private final AttendanceLeaveSyncService attendanceLeaveSyncService;
    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;

    public AttendanceClosingService(
            LeaveRequestRepository leaveRequestRepository,
            AttendanceLeaveSyncService attendanceLeaveSyncService,
            EmployeeRepository employeeRepository,
            AttendanceRepository attendanceRepository
    ) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.attendanceLeaveSyncService = attendanceLeaveSyncService;
        this.employeeRepository = employeeRepository;
        this.attendanceRepository = attendanceRepository;
    }

    @Transactional
    public void closeAttendanceForDate(LocalDate date) {
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            log.debug("Skip attendance closing for Sunday {}", date);
            return;
        }

        List<LeaveRequest> leaves = leaveRequestRepository
                .findByStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        LeaveStatus.APPROVED, date, date);
        Set<Long> synced = new HashSet<>();
        for (LeaveRequest lr : leaves) {
            Long empId = lr.getEmployeeId();
            if (synced.add(empId)) {
                attendanceLeaveSyncService.syncOnLeaveForApprovedRange(empId, date, date);
            }
        }

        List<Employee> active = employeeRepository.findByStatus(EmployeeStatus.ACTIVE);
        for (Employee e : active) {
            if (attendanceRepository.findByEmployeeIdAndAttendanceDate(e.getId(), date).isEmpty()) {
                Attendance a = new Attendance();
                a.setEmployeeId(e.getId());
                a.setAttendanceDate(date);
                a.setStatus(AttendanceStatus.ABSENT);
                a.setWorkHours(BigDecimal.ZERO);
                a.setOvertimeHours(BigDecimal.ZERO);
                attendanceRepository.save(a);
            }
        }
        log.info("Attendance closing completed for {}", date);
    }
}

package org.example.hrmsystem.service;

import org.example.hrmsystem.model.Attendance;
import org.example.hrmsystem.model.AttendanceStatus;
import org.example.hrmsystem.repository.AttendanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Đồng bộ chấm công ON_LEAVE khi đơn nghỉ được duyệt (bỏ Chủ nhật, giống rule tính ngày làm trong đơn).
 */
@Service
public class AttendanceLeaveSyncService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceLeaveSyncService.class);

    private final AttendanceRepository attendanceRepository;

    public AttendanceLeaveSyncService(AttendanceRepository attendanceRepository) {
        this.attendanceRepository = attendanceRepository;
    }

    @Transactional
    public void syncOnLeaveForApprovedRange(Long employeeId, LocalDate start, LocalDate end) {
        LocalDate d = start;
        while (!d.isAfter(end)) {
            if (d.getDayOfWeek() == DayOfWeek.SUNDAY) {
                d = d.plusDays(1);
                continue;
            }
            Optional<Attendance> opt = attendanceRepository.findByEmployeeIdAndAttendanceDate(employeeId, d);
            if (opt.isPresent()) {
                Attendance a = opt.get();
                if (a.getCheckIn() != null) {
                    log.debug("Skip ON_LEAVE sync {} {} — already checked in", employeeId, d);
                    d = d.plusDays(1);
                    continue;
                }
                a.setStatus(AttendanceStatus.ON_LEAVE);
                a.setWorkHours(BigDecimal.ZERO);
                a.setOvertimeHours(BigDecimal.ZERO);
                attendanceRepository.save(a);
            } else {
                Attendance a = new Attendance();
                a.setEmployeeId(employeeId);
                a.setAttendanceDate(d);
                a.setStatus(AttendanceStatus.ON_LEAVE);
                a.setWorkHours(BigDecimal.ZERO);
                a.setOvertimeHours(BigDecimal.ZERO);
                attendanceRepository.save(a);
            }
            d = d.plusDays(1);
        }
        log.info("Synced ON_LEAVE attendance for employee {} from {} to {}", employeeId, start, end);
    }
}

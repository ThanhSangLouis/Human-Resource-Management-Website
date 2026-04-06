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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        List<Attendance> existing = attendanceRepository.findByEmployeeIdAndAttendanceDateBetween(
                employeeId, start, end);
        Map<LocalDate, Attendance> byDate = new HashMap<>();
        for (Attendance a : existing) {
            byDate.put(a.getAttendanceDate(), a);
        }

        List<Attendance> toSave = new ArrayList<>();
        LocalDate d = start;
        while (!d.isAfter(end)) {
            if (d.getDayOfWeek() == DayOfWeek.SUNDAY) {
                d = d.plusDays(1);
                continue;
            }
            Attendance a = byDate.get(d);
            if (a != null && a.getCheckIn() != null) {
                log.debug("Skip ON_LEAVE sync {} {} — already checked in", employeeId, d);
                d = d.plusDays(1);
                continue;
            }
            if (a == null) {
                a = new Attendance();
                a.setEmployeeId(employeeId);
                a.setAttendanceDate(d);
            }
            a.setStatus(AttendanceStatus.ON_LEAVE);
            a.setWorkHours(BigDecimal.ZERO);
            a.setOvertimeHours(BigDecimal.ZERO);
            toSave.add(a);
            d = d.plusDays(1);
        }
        if (!toSave.isEmpty()) {
            attendanceRepository.saveAll(toSave);
        }
        log.info("Synced ON_LEAVE attendance for employee {} from {} to {} ({} rows)", employeeId, start, end, toSave.size());
    }
}

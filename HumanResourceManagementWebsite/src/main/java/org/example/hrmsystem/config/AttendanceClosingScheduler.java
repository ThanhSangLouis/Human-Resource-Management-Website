package org.example.hrmsystem.config;

import org.example.hrmsystem.service.AttendanceClosingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@ConditionalOnProperty(name = "hrm.attendance.daily-closing.enabled", havingValue = "true", matchIfMissing = true)
public class AttendanceClosingScheduler {

    private static final Logger log = LoggerFactory.getLogger(AttendanceClosingScheduler.class);

    private final AttendanceClosingService attendanceClosingService;

    public AttendanceClosingScheduler(AttendanceClosingService attendanceClosingService) {
        this.attendanceClosingService = attendanceClosingService;
    }

    /** 00:15 mỗi ngày — xử lý ngày làm việc vừa qua (theo server timezone). */
    @Scheduled(cron = "0 15 0 * * *")
    public void runPreviousDay() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        try {
            attendanceClosingService.closeAttendanceForDate(yesterday);
        } catch (Exception ex) {
            log.error("Attendance closing failed for {}: {}", yesterday, ex.getMessage(), ex);
        }
    }
}

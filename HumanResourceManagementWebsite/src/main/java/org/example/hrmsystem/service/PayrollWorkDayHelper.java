package org.example.hrmsystem.service;

import org.example.hrmsystem.model.Attendance;
import org.example.hrmsystem.model.AttendanceStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Quy đổi chấm công trong tháng lương thành "công" để hiển thị cạnh thực nhận (minh họa).
 * <ul>
 *   <li>PRESENT, LATE, ON_LEAVE → 1 công</li>
 *   <li>HALF_DAY → 0.5 công</li>
 *   <li>ABSENT → 0</li>
 * </ul>
 * Công thức lương demo trong {@link PayrollService} vẫn là cố định theo lương CB + thưởng − khấu trừ;
 * cột này chỉ giúp đối chiếu với dữ liệu chấm công.
 */
public final class PayrollWorkDayHelper {

    private PayrollWorkDayHelper() {
    }

    public static BigDecimal unitForStatus(AttendanceStatus s) {
        if (s == null) {
            return BigDecimal.ZERO;
        }
        return switch (s) {
            case PRESENT, LATE, ON_LEAVE -> BigDecimal.ONE;
            case HALF_DAY -> new BigDecimal("0.5");
            case ABSENT -> BigDecimal.ZERO;
        };
    }

    public static BigDecimal sumUnitsRounded(BigDecimal sum) {
        if (sum == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }
}

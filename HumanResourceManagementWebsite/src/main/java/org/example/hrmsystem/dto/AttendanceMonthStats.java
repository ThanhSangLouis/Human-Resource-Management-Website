package org.example.hrmsystem.dto;

import java.util.Map;

public class AttendanceMonthStats {

    private String month;
    private long totalRecords;
    private Map<String, Long> byStatus;
    private double attendanceRate;

    public AttendanceMonthStats() {}

    public AttendanceMonthStats(String month, long totalRecords,
                                Map<String, Long> byStatus, double attendanceRate) {
        this.month = month;
        this.totalRecords = totalRecords;
        this.byStatus = byStatus;
        this.attendanceRate = attendanceRate;
    }

    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }

    public long getTotalRecords() { return totalRecords; }
    public void setTotalRecords(long totalRecords) { this.totalRecords = totalRecords; }

    public Map<String, Long> getByStatus() { return byStatus; }
    public void setByStatus(Map<String, Long> byStatus) { this.byStatus = byStatus; }

    public double getAttendanceRate() { return attendanceRate; }
    public void setAttendanceRate(double attendanceRate) { this.attendanceRate = attendanceRate; }
}

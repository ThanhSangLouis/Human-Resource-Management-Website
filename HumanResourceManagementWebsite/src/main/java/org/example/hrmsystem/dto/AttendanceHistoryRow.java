package org.example.hrmsystem.dto;

import org.example.hrmsystem.model.AttendanceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class AttendanceHistoryRow {

    private Long id;
    private Long employeeId;
    private String employeeName;
    private LocalDate attendanceDate;
    private LocalDateTime checkIn;
    private LocalDateTime checkOut;
    private BigDecimal workHours;
    private BigDecimal overtimeHours;
    private AttendanceStatus status;
    private String note;

    public AttendanceHistoryRow() {}

    public AttendanceHistoryRow(
            Long id,
            Long employeeId,
            String employeeName,
            LocalDate attendanceDate,
            LocalDateTime checkIn,
            LocalDateTime checkOut,
            BigDecimal workHours,
            BigDecimal overtimeHours,
            AttendanceStatus status,
            String note
    ) {
        this.id = id;
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.attendanceDate = attendanceDate;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.workHours = workHours;
        this.overtimeHours = overtimeHours;
        this.status = status;
        this.note = note;
    }

    public Long getId() { return id; }
    public Long getEmployeeId() { return employeeId; }
    public String getEmployeeName() { return employeeName; }
    public LocalDate getAttendanceDate() { return attendanceDate; }
    public LocalDateTime getCheckIn() { return checkIn; }
    public LocalDateTime getCheckOut() { return checkOut; }
    public BigDecimal getWorkHours() { return workHours; }
    public BigDecimal getOvertimeHours() { return overtimeHours; }
    public AttendanceStatus getStatus() { return status; }
    public String getNote() { return note; }
}

package org.example.hrmsystem.dto;

import org.example.hrmsystem.model.AttendanceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class AttendanceResponse {

    private Long id;
    private Long employeeId;
    private LocalDate attendanceDate;
    private LocalDateTime checkIn;
    private LocalDateTime checkOut;
    private BigDecimal workHours;
    private BigDecimal overtimeHours;
    private AttendanceStatus status;
    private String note;
    private String message;

    public AttendanceResponse() {}

    public AttendanceResponse(
            Long id, Long employeeId, LocalDate attendanceDate,
            LocalDateTime checkIn, LocalDateTime checkOut,
            BigDecimal workHours, BigDecimal overtimeHours,
            AttendanceStatus status, String note, String message
    ) {
        this.id = id;
        this.employeeId = employeeId;
        this.attendanceDate = attendanceDate;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.workHours = workHours;
        this.overtimeHours = overtimeHours;
        this.status = status;
        this.note = note;
        this.message = message;
    }

    public Long getId() { return id; }
    public Long getEmployeeId() { return employeeId; }
    public LocalDate getAttendanceDate() { return attendanceDate; }
    public LocalDateTime getCheckIn() { return checkIn; }
    public LocalDateTime getCheckOut() { return checkOut; }
    public BigDecimal getWorkHours() { return workHours; }
    public BigDecimal getOvertimeHours() { return overtimeHours; }
    public AttendanceStatus getStatus() { return status; }
    public String getNote() { return note; }
    public String getMessage() { return message; }
}

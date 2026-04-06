package org.example.hrmsystem.dto;

import org.example.hrmsystem.model.LeaveStatus;
import org.example.hrmsystem.model.LeaveType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class LeaveRequestResponse {

    private Long id;
    private Long employeeId;
    private String employeeName;
    private LeaveType leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private int totalDays;
    private String reason;
    private LeaveStatus status;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private String message;

    public LeaveRequestResponse() {}

    public LeaveRequestResponse(
            Long id, Long employeeId, String employeeName,
            LeaveType leaveType, LocalDate startDate, LocalDate endDate,
            int totalDays, String reason, LeaveStatus status,
            Long approvedBy,
            LocalDateTime approvedAt, LocalDateTime createdAt, String message
    ) {
        this.id = id;
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.leaveType = leaveType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.totalDays = totalDays;
        this.reason = reason;
        this.status = status;
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
        this.createdAt = createdAt;
        this.message = message;
    }

    public Long getId() { return id; }
    public Long getEmployeeId() { return employeeId; }
    public String getEmployeeName() { return employeeName; }
    public LeaveType getLeaveType() { return leaveType; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public int getTotalDays() { return totalDays; }
    public String getReason() { return reason; }
    public LeaveStatus getStatus() { return status; }
    public Long getApprovedBy() { return approvedBy; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getMessage() { return message; }
}

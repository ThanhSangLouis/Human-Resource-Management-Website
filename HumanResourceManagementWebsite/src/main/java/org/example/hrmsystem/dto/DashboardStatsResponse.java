package org.example.hrmsystem.dto;

import java.util.List;
import java.util.Map;

public class DashboardStatsResponse {

    private long totalEmployees;
    private Map<String, Long> byStatus;
    private List<DepartmentStatRow> byDepartment;
    private long totalDepartments;
    private AttendanceMonthStats attendanceMonth;
    private String generatedAt;

    public DashboardStatsResponse() {}

    public long getTotalEmployees() { return totalEmployees; }
    public void setTotalEmployees(long totalEmployees) { this.totalEmployees = totalEmployees; }

    public Map<String, Long> getByStatus() { return byStatus; }
    public void setByStatus(Map<String, Long> byStatus) { this.byStatus = byStatus; }

    public List<DepartmentStatRow> getByDepartment() { return byDepartment; }
    public void setByDepartment(List<DepartmentStatRow> byDepartment) { this.byDepartment = byDepartment; }

    public long getTotalDepartments() { return totalDepartments; }
    public void setTotalDepartments(long totalDepartments) { this.totalDepartments = totalDepartments; }

    public AttendanceMonthStats getAttendanceMonth() { return attendanceMonth; }
    public void setAttendanceMonth(AttendanceMonthStats attendanceMonth) { this.attendanceMonth = attendanceMonth; }

    public String getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(String generatedAt) { this.generatedAt = generatedAt; }
}

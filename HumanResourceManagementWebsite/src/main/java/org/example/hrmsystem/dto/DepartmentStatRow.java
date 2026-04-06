package org.example.hrmsystem.dto;

public class DepartmentStatRow {

    private Long departmentId;
    private String departmentName;
    private long count;

    public DepartmentStatRow() {}

    public DepartmentStatRow(Long departmentId, String departmentName, long count) {
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.count = count;
    }

    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }
}

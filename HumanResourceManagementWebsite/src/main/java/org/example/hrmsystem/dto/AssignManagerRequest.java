package org.example.hrmsystem.dto;

/**
 * Request body cho PATCH /api/departments/{id}/manager
 * managerId = null → bỏ gán trưởng phòng
 */
public class AssignManagerRequest {

    private Long managerId;

    public Long getManagerId() { return managerId; }
    public void setManagerId(Long managerId) { this.managerId = managerId; }
}

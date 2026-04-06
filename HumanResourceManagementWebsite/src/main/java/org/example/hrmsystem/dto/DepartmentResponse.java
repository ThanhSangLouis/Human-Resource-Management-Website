package org.example.hrmsystem.dto;

import java.time.LocalDateTime;

public class DepartmentResponse {

    private Long id;
    private String name;
    private String description;
    private Long managerId;
    private String managerName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public DepartmentResponse() {}

    public DepartmentResponse(Long id, String name, String description,
                               Long managerId, String managerName,
                               LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.managerId = managerId;
        this.managerName = managerName;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Long getManagerId() { return managerId; }
    public String getManagerName() { return managerName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

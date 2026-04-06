package org.example.hrmsystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class DepartmentRequest {

    @NotBlank(message = "Tên phòng ban không được để trống")
    @Size(max = 150, message = "Tên phòng ban tối đa 150 ký tự")
    private String name;

    private String description;

    private Long managerId;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getManagerId() { return managerId; }
    public void setManagerId(Long managerId) { this.managerId = managerId; }
}

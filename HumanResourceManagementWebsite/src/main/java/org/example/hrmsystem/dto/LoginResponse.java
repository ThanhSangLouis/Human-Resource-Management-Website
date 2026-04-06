package org.example.hrmsystem.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        String username,
        String role,
        Long employeeId
) {
}

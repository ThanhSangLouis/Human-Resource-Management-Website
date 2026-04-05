package org.example.hrmsystem.dto;

public record LoginResult(
        LoginResponse loginResponse,
        String refreshToken
) {
}

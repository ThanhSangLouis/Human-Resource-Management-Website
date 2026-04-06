package org.example.hrmsystem.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.example.hrmsystem.dto.LoginRequest;
import org.example.hrmsystem.dto.LoginResult;
import org.example.hrmsystem.dto.LoginResponse;
import org.example.hrmsystem.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refreshToken";

    private final AuthService authService;
    private final long refreshTokenMaxAgeSeconds;
    private final boolean refreshTokenSecure;

    public AuthController(
            AuthService authService,
            @Value("${security.refresh-token.max-age-seconds:604800}") long refreshTokenMaxAgeSeconds,
            @Value("${security.refresh-token.secure:false}") boolean refreshTokenSecure
    ) {
        this.authService = authService;
        this.refreshTokenMaxAgeSeconds = refreshTokenMaxAgeSeconds;
        this.refreshTokenSecure = refreshTokenSecure;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        LoginResult result = authService.login(request);
        response.addHeader(HttpHeaders.SET_COOKIE, buildRefreshCookie(result.refreshToken(), refreshTokenMaxAgeSeconds).toString());
        return ResponseEntity.ok(result.loginResponse());
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response
    ) {
        LoginResult result = authService.refresh(refreshToken);
        response.addHeader(HttpHeaders.SET_COOKIE, buildRefreshCookie(result.refreshToken(), refreshTokenMaxAgeSeconds).toString());
        return ResponseEntity.ok(result.loginResponse());
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response
    ) {
        authService.logout(refreshToken);
        response.addHeader(HttpHeaders.SET_COOKIE, buildRefreshCookie("", 0).toString());
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    private ResponseCookie buildRefreshCookie(String tokenValue, long maxAgeSeconds) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, tokenValue)
                .httpOnly(true)
                .secure(refreshTokenSecure)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(maxAgeSeconds)
                .build();
    }
}

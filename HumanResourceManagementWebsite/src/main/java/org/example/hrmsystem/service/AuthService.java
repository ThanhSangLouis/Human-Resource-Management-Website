package org.example.hrmsystem.service;

import org.example.hrmsystem.dto.LoginRequest;
import org.example.hrmsystem.dto.LoginResult;
import org.example.hrmsystem.dto.LoginResponse;
import org.example.hrmsystem.exception.InvalidRefreshTokenException;
import org.example.hrmsystem.exception.RefreshTokenExpiredException;
import org.example.hrmsystem.model.RefreshToken;
import org.example.hrmsystem.model.UserAccount;
import org.example.hrmsystem.repository.RefreshTokenRepository;
import org.example.hrmsystem.repository.UserAccountRepository;
import org.example.hrmsystem.security.AppUserDetails;
import org.example.hrmsystem.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserAccountRepository userAccountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long refreshTokenMaxAgeSeconds;

    public AuthService(
            UserAccountRepository userAccountRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @org.springframework.beans.factory.annotation.Value("${security.refresh-token.max-age-seconds:604800}")
            long refreshTokenMaxAgeSeconds
    ) {
        this.userAccountRepository = userAccountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenMaxAgeSeconds = refreshTokenMaxAgeSeconds;
    }

    @Transactional
    public LoginResult login(LoginRequest request) {
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        UserAccount account = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("Login failed: username [{}] not found", username);
                    return new BadCredentialsException("Invalid username or password");
                });
        if (!account.isActive()) {
            log.warn("Login failed: username [{}] is inactive", username);
            throw new DisabledException("Account is inactive");
        }

        String storedHash = account.getPassword() == null ? "" : account.getPassword().trim();
        String rawPassword = request.getPassword() == null ? "" : request.getPassword();
        if (!passwordEncoder.matches(rawPassword, storedHash)) {
            log.warn("Login failed: password mismatch for username [{}], hashLength={}", username, storedHash.length());
            throw new BadCredentialsException("Invalid username or password");
        }

        String refreshToken = createAndStoreRefreshToken(account.getId());
        LoginResponse loginResponse = createLoginResponse(account);
        return new LoginResult(loginResponse, refreshToken);
    }

    @Transactional
    public LoginResult refresh(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            throw new InvalidRefreshTokenException("Invalid refresh token");
        }

        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue.trim())
                .orElseThrow(() -> new InvalidRefreshTokenException("Invalid refresh token"));
        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.deleteByToken(refreshTokenValue.trim());
            throw new RefreshTokenExpiredException("Refresh token expired");
        }

        UserAccount account = userAccountRepository.findById(refreshToken.getUserId())
                .orElseThrow(() -> new InvalidRefreshTokenException("Invalid refresh token"));
        if (!account.isActive()) {
            throw new DisabledException("Account is inactive");
        }

        String newRefreshToken = createAndStoreRefreshToken(account.getId());
        LoginResponse loginResponse = createLoginResponse(account);
        return new LoginResult(loginResponse, newRefreshToken);
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            return;
        }
        refreshTokenRepository.deleteByToken(refreshTokenValue.trim());
    }

    private LoginResponse createLoginResponse(UserAccount account) {
        AppUserDetails userDetails = new AppUserDetails(account);
        String accessToken = jwtService.generateToken(userDetails);

        return new LoginResponse(
                accessToken,
                "Bearer",
                userDetails.getUsername(),
                userDetails.getRole(),
                userDetails.getEmployeeId()
        );
    }

    private String createAndStoreRefreshToken(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(userId);
        refreshToken.setToken(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
        refreshToken.setExpiresAt(LocalDateTime.now().plusSeconds(refreshTokenMaxAgeSeconds));
        refreshTokenRepository.save(refreshToken);
        return refreshToken.getToken();
    }
}

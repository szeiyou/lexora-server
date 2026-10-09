package com.songzy.dicserver.service;

import com.songzy.dicserver.dto.AuthResponse;
import com.songzy.dicserver.dto.AuthUserDTO;
import com.songzy.dicserver.config.AuthProperties;
import com.songzy.dicserver.exception.BusinessException;
import com.songzy.dicserver.model.AppUser;
import com.songzy.dicserver.model.UserStatus;
import com.songzy.dicserver.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RefreshSessionService refreshSessionService;
    private final AuthProperties authProperties;

    @Transactional
    public AuthResponse register(String username, String rawPassword) {
        String usernameHash = AppUser.hashUsername(username);
        if (appUserRepository.existsByUsernameHash(usernameHash)) {
            throw new BusinessException("用户名已存在", "USERNAME_ALREADY_EXISTS");
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setStatus(UserStatus.ACTIVE);

        AppUser savedUser;
        try {
            savedUser = appUserRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("用户名已存在", "USERNAME_ALREADY_EXISTS");
        }
        return issueTokens(savedUser, refreshSessionService.createSession(savedUser));
    }

    @Transactional
    public AuthResponse login(String username, String rawPassword) {
        AppUser user = appUserRepository.findByUsernameHash(AppUser.hashUsername(username))
                .orElseThrow(this::invalidCredentials);
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw invalidCredentials();
        }
        return issueTokens(user, refreshSessionService.createSession(user));
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        RefreshSessionService.IssuedRefreshToken issuedRefreshToken =
                refreshSessionService.rotateSession(refreshToken);
        return issueTokens(issuedRefreshToken.user(), issuedRefreshToken);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshSessionService.revokeSession(refreshToken);
    }

    private AuthResponse issueTokens(AppUser user, RefreshSessionService.IssuedRefreshToken issuedRefreshToken) {
        String accessToken = jwtTokenService.issueAccessToken(user.getId(), user.getUsername());
        return new AuthResponse(
                accessToken,
                issuedRefreshToken.refreshToken(),
                "Bearer",
                authProperties.getAccessTokenTtlSeconds(),
                new AuthUserDTO(user.getId(), user.getUsername())
        );
    }

    private BusinessException invalidCredentials() {
        return new BusinessException("用户名或密码错误", "INVALID_CREDENTIALS");
    }
}

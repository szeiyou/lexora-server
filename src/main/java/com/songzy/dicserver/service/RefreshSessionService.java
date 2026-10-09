package com.songzy.dicserver.service;

import com.songzy.dicserver.config.AuthProperties;
import com.songzy.dicserver.exception.BusinessException;
import com.songzy.dicserver.model.AppUser;
import com.songzy.dicserver.model.RefreshSession;
import com.songzy.dicserver.repository.RefreshSessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshSessionService {

    private final RefreshSessionRepository refreshSessionRepository;
    private final AuthProperties authProperties;

    @Transactional
    public IssuedRefreshToken createSession(AppUser user) {
        String refreshToken = generateRefreshToken();
        LocalDateTime now = LocalDateTime.now();

        RefreshSession session = new RefreshSession();
        session.setUser(user);
        session.setTokenHash(hashToken(refreshToken));
        session.setExpiresAt(now.plusSeconds(authProperties.getRefreshTokenTtlSeconds()));
        session.setLastUsedAt(now);
        refreshSessionRepository.save(session);

        return new IssuedRefreshToken(refreshToken, user);
    }

    @Transactional
    public IssuedRefreshToken rotateSession(String refreshToken) {
        RefreshSession session = requireActiveSession(refreshToken);
        LocalDateTime now = LocalDateTime.now();
        session.setLastUsedAt(now);
        session.setRevokedAt(now);
        refreshSessionRepository.save(session);
        return createSession(session.getUser());
    }

    @Transactional
    public void revokeSession(String refreshToken) {
        refreshSessionRepository.findByTokenHash(hashToken(refreshToken)).ifPresent(session -> {
            LocalDateTime now = LocalDateTime.now();
            session.setLastUsedAt(now);
            if (session.getRevokedAt() == null) {
                session.setRevokedAt(now);
            }
            refreshSessionRepository.save(session);
        });
    }

    private RefreshSession requireActiveSession(String refreshToken) {
        RefreshSession session = refreshSessionRepository.findByTokenHashForUpdate(hashToken(refreshToken))
                .orElseThrow(() -> new BusinessException("刷新令牌无效", "REFRESH_TOKEN_INVALID"));
        LocalDateTime now = LocalDateTime.now();
        if (session.getRevokedAt() != null || session.getExpiresAt().isBefore(now)) {
            throw new BusinessException("刷新令牌无效", "REFRESH_TOKEN_INVALID");
        }
        return session;
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[authProperties.getRefreshTokenBytes()];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String refreshToken) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record IssuedRefreshToken(String refreshToken, AppUser user) {
    }
}

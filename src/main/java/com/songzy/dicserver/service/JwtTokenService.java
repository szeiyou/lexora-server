package com.songzy.dicserver.service;

import com.songzy.dicserver.config.AuthProperties;
import com.songzy.dicserver.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private final AuthProperties authProperties;
    private final SecretKey signingKey;

    public JwtTokenService(AuthProperties authProperties) {
        this.authProperties = authProperties;
        this.signingKey = Keys.hmacShaKeyFor(validateJwtSecret(authProperties).getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(Long userId, String username) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(authProperties.getAccessTokenTtlSeconds());
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public AccessTokenClaims parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new AccessTokenClaims(Long.valueOf(claims.getSubject()), claims.get("username", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException("访问令牌无效", "INVALID_ACCESS_TOKEN");
        }
    }

    public record AccessTokenClaims(Long userId, String username) {
    }

    private String validateJwtSecret(AuthProperties authProperties) {
        String jwtSecret = authProperties.getJwtSecret();
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("app.auth.jwt-secret must be configured and non-blank");
        }
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.auth.jwt-secret must be at least 32 bytes");
        }
        return jwtSecret;
    }
}

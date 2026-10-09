package com.songzy.dicserver.service;

import com.songzy.dicserver.config.AuthProperties;
import com.songzy.dicserver.exception.BusinessException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    @Test
    void issueAccessTokenShouldRoundTripThroughParseAccessToken() {
        JwtTokenService jwtTokenService = new JwtTokenService(validProperties());

        String token = jwtTokenService.issueAccessToken(42L, "alice");

        JwtTokenService.AccessTokenClaims claims = jwtTokenService.parseAccessToken(token);
        assertThat(claims.userId()).isEqualTo(42L);
        assertThat(claims.username()).isEqualTo("alice");
    }

    @Test
    void constructorShouldRejectBlankJwtSecret() {
        AuthProperties properties = validProperties();
        properties.setJwtSecret("   ");

        assertThatThrownBy(() -> new JwtTokenService(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.auth.jwt-secret");
    }

    @Test
    void constructorShouldRejectTooShortJwtSecret() {
        AuthProperties properties = validProperties();
        properties.setJwtSecret("too-short-secret");

        assertThatThrownBy(() -> new JwtTokenService(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.auth.jwt-secret");
    }

    @Test
    void parseAccessTokenShouldMapMalformedTokenToInvalidAccessToken() {
        JwtTokenService jwtTokenService = new JwtTokenService(validProperties());

        assertThatThrownBy(() -> jwtTokenService.parseAccessToken("not-a-jwt"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("INVALID_ACCESS_TOKEN");
    }

    @Test
    void parseAccessTokenShouldMapExpiredTokenToInvalidAccessToken() {
        AuthProperties properties = validProperties();
        JwtTokenService jwtTokenService = new JwtTokenService(properties);
        Instant now = Instant.now();
        String expiredToken = Jwts.builder()
                .subject("42")
                .claim("username", "alice")
                .issuedAt(Date.from(now.minusSeconds(120)))
                .expiration(Date.from(now.minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThatThrownBy(() -> jwtTokenService.parseAccessToken(expiredToken))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("INVALID_ACCESS_TOKEN");
    }

    @Test
    void parseAccessTokenShouldMapWrongSignatureToInvalidAccessToken() {
        JwtTokenService jwtTokenService = new JwtTokenService(validProperties());
        String wrongSignatureToken = Jwts.builder()
                .subject("42")
                .claim("username", "alice")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(900)))
                .signWith(Keys.hmacShaKeyFor("different-secret-key-different-secret-key".getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThatThrownBy(() -> jwtTokenService.parseAccessToken(wrongSignatureToken))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("INVALID_ACCESS_TOKEN");
    }

    private static AuthProperties validProperties() {
        AuthProperties properties = new AuthProperties();
        properties.setJwtSecret("0123456789abcdef0123456789abcdef0123456789abcdef");
        properties.setAccessTokenTtlSeconds(900L);
        properties.setRefreshTokenTtlSeconds(604800L);
        properties.setRefreshTokenBytes(32);
        return properties;
    }
}

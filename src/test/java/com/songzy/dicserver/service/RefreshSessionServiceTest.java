package com.songzy.dicserver.service;

import com.songzy.dicserver.config.AuthProperties;
import com.songzy.dicserver.exception.BusinessException;
import com.songzy.dicserver.model.AppUser;
import com.songzy.dicserver.model.RefreshSession;
import com.songzy.dicserver.model.UserStatus;
import com.songzy.dicserver.repository.RefreshSessionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshSessionServiceTest {

    @Mock
    private RefreshSessionRepository refreshSessionRepository;

    @Mock
    private AuthProperties authProperties;

    @InjectMocks
    private RefreshSessionService refreshSessionService;

    @Test
    void createSessionShouldHashTokenAtRest() {
        AppUser user = activeUser(42L, "alice");
        when(authProperties.getRefreshTokenBytes()).thenReturn(32);
        when(authProperties.getRefreshTokenTtlSeconds()).thenReturn(3600L);

        LocalDateTime before = LocalDateTime.now();
        RefreshSessionService.IssuedRefreshToken issuedRefreshToken = refreshSessionService.createSession(user);
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<RefreshSession> sessionCaptor = ArgumentCaptor.forClass(RefreshSession.class);
        verify(refreshSessionRepository).save(sessionCaptor.capture());
        RefreshSession savedSession = sessionCaptor.getValue();
        assertThat(issuedRefreshToken.user()).isSameAs(user);
        assertThat(issuedRefreshToken.refreshToken()).isNotBlank();
        assertThat(savedSession.getUser()).isSameAs(user);
        assertThat(savedSession.getTokenHash()).isNotEqualTo(issuedRefreshToken.refreshToken());
        assertThat(savedSession.getTokenHash()).matches("[0-9a-f]{64}");
        assertThat(savedSession.getRevokedAt()).isNull();
        assertThat(savedSession.getLastUsedAt()).isBetween(before, after);
        assertThat(savedSession.getExpiresAt()).isBetween(before.plusSeconds(3600L), after.plusSeconds(3600L));
    }

    @Test
    void rotateSessionShouldRejectMissingToken() {
        when(refreshSessionRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshSessionService.rotateSession("missing-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("刷新令牌无效")
                .extracting("code")
                .isEqualTo("REFRESH_TOKEN_INVALID");
    }

    @Test
    void rotateSessionShouldRejectRevokedToken() {
        RefreshSession session = activeSession(activeUser(42L, "alice"));
        session.setRevokedAt(LocalDateTime.now().minusMinutes(1));
        when(refreshSessionRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> refreshSessionService.rotateSession("revoked-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("刷新令牌无效")
                .extracting("code")
                .isEqualTo("REFRESH_TOKEN_INVALID");
    }

    @Test
    void rotateSessionShouldRejectExpiredToken() {
        RefreshSession session = activeSession(activeUser(42L, "alice"));
        session.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(refreshSessionRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> refreshSessionService.rotateSession("expired-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("刷新令牌无效")
                .extracting("code")
                .isEqualTo("REFRESH_TOKEN_INVALID");
    }

    @Test
    void rotateSessionShouldRevokeOldSessionAndIssueNewOne() {
        AppUser user = activeUser(42L, "alice");
        RefreshSession currentSession = activeSession(user);
        currentSession.setTokenHash("old-token-hash");

        when(authProperties.getRefreshTokenBytes()).thenReturn(32);
        when(authProperties.getRefreshTokenTtlSeconds()).thenReturn(7200L);
        when(refreshSessionRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(currentSession));

        LocalDateTime before = LocalDateTime.now();
        RefreshSessionService.IssuedRefreshToken issuedRefreshToken = refreshSessionService.rotateSession("refresh-token");
        LocalDateTime after = LocalDateTime.now();

        verify(refreshSessionRepository, never()).findByTokenHash(anyString());

        ArgumentCaptor<RefreshSession> sessionCaptor = ArgumentCaptor.forClass(RefreshSession.class);
        verify(refreshSessionRepository, times(2)).save(sessionCaptor.capture());
        List<RefreshSession> savedSessions = sessionCaptor.getAllValues();
        RefreshSession revokedSession = savedSessions.get(0);
        RefreshSession replacementSession = savedSessions.get(1);

        assertThat(revokedSession).isSameAs(currentSession);
        assertThat(revokedSession.getRevokedAt()).isBetween(before, after);
        assertThat(revokedSession.getLastUsedAt()).isBetween(before, after);

        assertThat(issuedRefreshToken.user()).isSameAs(user);
        assertThat(issuedRefreshToken.refreshToken()).isNotBlank();
        assertThat(replacementSession.getUser()).isSameAs(user);
        assertThat(replacementSession.getTokenHash()).matches("[0-9a-f]{64}");
        assertThat(replacementSession.getTokenHash()).isNotEqualTo("old-token-hash");
        assertThat(replacementSession.getTokenHash()).isNotEqualTo(issuedRefreshToken.refreshToken());
        assertThat(replacementSession.getRevokedAt()).isNull();
        assertThat(replacementSession.getLastUsedAt()).isBetween(before, after);
        assertThat(replacementSession.getExpiresAt()).isBetween(before.plusSeconds(7200L), after.plusSeconds(7200L));
    }

    private static AppUser activeUser(Long id, String username) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername(username);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private static RefreshSession activeSession(AppUser user) {
        RefreshSession session = new RefreshSession();
        session.setUser(user);
        session.setExpiresAt(LocalDateTime.now().plusHours(1));
        session.setLastUsedAt(LocalDateTime.now().minusMinutes(1));
        return session;
    }
}

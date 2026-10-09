package com.songzy.dicserver.service;

import com.songzy.dicserver.dto.AuthResponse;
import com.songzy.dicserver.config.AuthProperties;
import com.songzy.dicserver.exception.BusinessException;
import com.songzy.dicserver.model.AppUser;
import com.songzy.dicserver.model.UserStatus;
import com.songzy.dicserver.repository.AppUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private RefreshSessionService refreshSessionService;

    @Mock
    private AuthProperties authProperties;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerShouldRejectDuplicateUsername() {
        when(appUserRepository.existsByUsernameHash(AppUser.hashUsername("alice"))).thenReturn(true);

        assertThatThrownBy(() -> authService.register("alice", "secret"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户名已存在")
                .extracting("code")
                .isEqualTo("USERNAME_ALREADY_EXISTS");

        verify(appUserRepository, never()).save(any(AppUser.class));
    }

    @Test
    void registerShouldPersistEncodedPasswordAndReturnTokens() {
        when(appUserRepository.existsByUsernameHash(AppUser.hashUsername("alice"))).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("encoded-secret");
        when(authProperties.getAccessTokenTtlSeconds()).thenReturn(900L);
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            user.setId(42L);
            return user;
        });
        when(jwtTokenService.issueAccessToken(42L, "alice")).thenReturn("access-token");
        when(refreshSessionService.createSession(any(AppUser.class)))
                .thenReturn(new RefreshSessionService.IssuedRefreshToken("refresh-token", activeUser(42L, "alice")));

        AuthResponse response = authService.register("alice", "secret");

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(userCaptor.capture());
        AppUser savedUser = userCaptor.getValue();
        assertThat(savedUser.getUsername()).isEqualTo("alice");
        assertThat(savedUser.getPasswordHash()).isEqualTo("encoded-secret");
        assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900L);
        assertThat(response.user().id()).isEqualTo(42L);
        assertThat(response.user().username()).isEqualTo("alice");
    }

    @Test
    void registerShouldMapDuplicateSaveRaceToBusinessException() {
        when(appUserRepository.existsByUsernameHash(AppUser.hashUsername("alice"))).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("encoded-secret");
        when(appUserRepository.save(any(AppUser.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> authService.register("alice", "secret"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户名已存在")
                .extracting("code")
                .isEqualTo("USERNAME_ALREADY_EXISTS");
    }

    @Test
    void registerShouldNotMapRefreshSessionFailureToDuplicateUsername() {
        when(appUserRepository.existsByUsernameHash(AppUser.hashUsername("alice"))).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("encoded-secret");
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            user.setId(42L);
            return user;
        });
        when(refreshSessionService.createSession(any(AppUser.class)))
                .thenThrow(new DataIntegrityViolationException("refresh session insert failed"));

        assertThatThrownBy(() -> authService.register("alice", "secret"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("refresh session");
    }

    @Test
    void loginShouldReturnAccessTokenRefreshTokenAndUser() {
        AppUser user = activeUser(42L, "alice");
        user.setPasswordHash("encoded-secret");

        when(appUserRepository.findByUsernameHash(AppUser.hashUsername("alice"))).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "encoded-secret")).thenReturn(true);
        when(authProperties.getAccessTokenTtlSeconds()).thenReturn(900L);
        when(jwtTokenService.issueAccessToken(42L, "alice")).thenReturn("access-token");
        when(refreshSessionService.createSession(user))
                .thenReturn(new RefreshSessionService.IssuedRefreshToken("refresh-token", user));

        AuthResponse response = authService.login("alice", "secret");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900L);
        assertThat(response.user().id()).isEqualTo(42L);
        assertThat(response.user().username()).isEqualTo("alice");
    }

    @Test
    void loginShouldRejectInvalidCredentialsWhenPasswordDoesNotMatch() {
        AppUser user = activeUser(42L, "alice");
        user.setPasswordHash("encoded-secret");

        when(appUserRepository.findByUsernameHash(AppUser.hashUsername("alice"))).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-secret", "encoded-secret")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("alice", "wrong-secret"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户名或密码错误")
                .extracting("code")
                .isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void loginShouldRejectDifferentUsernameCase() {
        when(appUserRepository.findByUsernameHash(AppUser.hashUsername("Alice"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("Alice", "secret"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户名或密码错误")
                .extracting("code")
                .isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void refreshShouldRotateRefreshTokenAndIssueNewAccessToken() {
        AppUser user = activeUser(42L, "alice");

        when(authProperties.getAccessTokenTtlSeconds()).thenReturn(900L);
        when(refreshSessionService.rotateSession("refresh-token"))
                .thenReturn(new RefreshSessionService.IssuedRefreshToken("refresh-token-2", user));
        when(jwtTokenService.issueAccessToken(42L, "alice")).thenReturn("access-token-2");

        AuthResponse response = authService.refresh("refresh-token");

        assertThat(response.accessToken()).isEqualTo("access-token-2");
        assertThat(response.refreshToken()).isEqualTo("refresh-token-2");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900L);
        assertThat(response.user().id()).isEqualTo(42L);
        assertThat(response.user().username()).isEqualTo("alice");
    }

    @Test
    void logoutShouldRevokeRefreshToken() {
        authService.logout("refresh-token");

        verify(refreshSessionService).revokeSession("refresh-token");
    }

    private static AppUser activeUser(Long id, String username) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername(username);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}

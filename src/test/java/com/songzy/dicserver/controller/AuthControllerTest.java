package com.songzy.dicserver.controller;

import com.songzy.dicserver.dto.AuthResponse;
import com.songzy.dicserver.dto.AuthUserDTO;
import com.songzy.dicserver.exception.GlobalExceptionHandler;
import com.songzy.dicserver.security.CurrentUserPrincipal;
import com.songzy.dicserver.service.AuthService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @Test
    void registerShouldTrimUsernameBeforeDelegating() throws Exception {
        when(authService.register("alice", "Password123"))
                .thenReturn(authResponse("alice"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"  alice  ","password":"Password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));

        verify(authService).register("alice", "Password123");
    }

    @Test
    void loginShouldTrimUsernameBeforeDelegating() throws Exception {
        when(authService.login("alice", "Password123"))
                .thenReturn(authResponse("alice"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"  alice  ","password":"Password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));

        verify(authService).login("alice", "Password123");
    }

    @Test
    void refreshShouldDelegateToAuthService() throws Exception {
        when(authService.refresh("refresh-token"))
                .thenReturn(authResponse("alice"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"refresh-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        verify(authService).refresh("refresh-token");
    }

    @Test
    void logoutShouldDelegateToAuthService() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"refresh-token"}
                                """))
                .andExpect(status().isOk());

        verify(authService).logout("refresh-token");
    }

    @Test
    void meShouldReturnCurrentUser() throws Exception {
        CurrentUserPrincipal principal = new CurrentUserPrincipal(7L, "alice");
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                principal,
                "access-token",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        ));
        SecurityContextHolder.setContext(context);

        try {
            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(7))
                    .andExpect(jsonPath("$.username").value("alice"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static AuthResponse authResponse(String username) {
        return new AuthResponse("access-token", "refresh-token", "Bearer", 900L, new AuthUserDTO(7L, username));
    }
}

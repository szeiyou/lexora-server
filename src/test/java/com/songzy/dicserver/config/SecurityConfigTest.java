package com.songzy.dicserver.config;

import com.songzy.dicserver.dto.AuthResponse;
import com.songzy.dicserver.dto.AuthUserDTO;
import com.songzy.dicserver.dto.QueryHistorySummaryDTO;
import com.songzy.dicserver.service.AuthService;
import com.songzy.dicserver.service.JwtTokenService;
import com.songzy.dicserver.service.QueryHistoryService;
import com.songzy.dicserver.service.TextTranslationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private QueryHistoryService queryHistoryService;

    @MockitoBean
    private TextTranslationService textTranslationService;

    @Test
    void unauthenticatedHistoryRequestShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedHistoryRequestWithBearerTokenShouldSucceed() throws Exception {
        when(jwtTokenService.parseAccessToken("good-token"))
                .thenReturn(new JwtTokenService.AccessTokenClaims(1L, "alice"));
        when(queryHistoryService.getHistory(eq(1L), any())).thenReturn(new PageImpl<QueryHistorySummaryDTO>(List.of()));

        mockMvc.perform(get("/api/v1/history")
                        .header("Authorization", "Bearer good-token"))
                .andExpect(status().isOk());
    }

    @Test
    void publicRegisterRouteShouldBeAccessibleWithoutAuth() throws Exception {
        when(authService.register("alice", "Password123"))
                .thenReturn(authResponse("alice"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"Password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    void publicLoginRouteShouldBeAccessibleWithoutAuth() throws Exception {
        when(authService.login("alice", "Password123"))
                .thenReturn(authResponse("alice"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"Password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
    }

    @Test
    void publicRefreshRouteShouldBeAccessibleWithoutAuth() throws Exception {
        when(authService.refresh("refresh-token"))
                .thenReturn(authResponse("alice"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"refresh-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void logoutShouldRequireAuth() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"refresh-token"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authMeShouldReflectBearerPrincipal() throws Exception {
        when(jwtTokenService.parseAccessToken("good-token"))
                .thenReturn(new JwtTokenService.AccessTokenClaims(7L, "alice"));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer good-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void actuatorShouldBeAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void swaggerShouldBeAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    private static AuthResponse authResponse(String username) {
        return new AuthResponse("access-token", "refresh-token", "Bearer", 900L, new AuthUserDTO(7L, username));
    }
}

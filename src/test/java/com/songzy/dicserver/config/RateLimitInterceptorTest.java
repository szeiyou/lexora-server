package com.songzy.dicserver.config;

import com.songzy.dicserver.exception.BusinessException;
import com.songzy.dicserver.security.CurrentUserPrincipal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitInterceptorTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void differentAuthenticatedUsersShouldNotShareRateLimitBucketForSamePath() throws Exception {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(1);

        authenticateAs(7L, "alice");
        assertThat(interceptor.preHandle(request("/api/v1/history", "127.0.0.1"), new MockHttpServletResponse(), new Object()))
                .isTrue();

        authenticateAs(8L, "bob");
        assertThat(interceptor.preHandle(request("/api/v1/history", "127.0.0.1"), new MockHttpServletResponse(), new Object()))
                .isTrue();
    }

    @Test
    void anonymousAuthRequestsShouldRemainIpScoped() {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(1);

        assertThat(interceptor.preHandle(request("/api/v1/auth/login", "127.0.0.1"), new MockHttpServletResponse(), new Object()))
                .isTrue();
        assertThatThrownBy(() -> interceptor.preHandle(
                request("/api/v1/auth/login", "127.0.0.1"),
                new MockHttpServletResponse(),
                new Object()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("RATE_LIMIT_EXCEEDED");
    }

    private static void authenticateAs(Long userId, String username) {
        CurrentUserPrincipal principal = new CurrentUserPrincipal(userId, username);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                "access-token",
                List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static MockHttpServletRequest request(String path, String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}

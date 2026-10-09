package com.songzy.dicserver.config;

import com.songzy.dicserver.exception.BusinessException;
import com.songzy.dicserver.security.CurrentUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final int maxRequestsPerMinute;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public RateLimitInterceptor(@Value("${rate-limit.requests-per-minute:60}") int maxRequestsPerMinute) {
        this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String key = getClientKey(request);
        WindowCounter counter = counters.computeIfAbsent(key, k -> new WindowCounter());
        counter.increment();
        if (counter.getCount() > maxRequestsPerMinute) {
            throw new BusinessException("请求过于频繁，请稍后再试", "RATE_LIMIT_EXCEEDED");
        }
        return true;
    }

    private String getClientKey(HttpServletRequest request) {
        String path = request.getRequestURI();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUserPrincipal currentUser) {
            return "user:" + currentUser.userId() + ":" + path;
        }

        String ip = request.getRemoteAddr();
        if ("/api/v1/auth/login".equals(path)
                || "/api/v1/auth/register".equals(path)
                || "/api/v1/auth/refresh".equals(path)) {
            return "ip:" + ip + ":" + path;
        }
        return "anon:" + ip + ":" + path;
    }

    private static class WindowCounter {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long windowStart = System.currentTimeMillis() / 60_000;

        void increment() {
            long currentWindow = System.currentTimeMillis() / 60_000;
            if (currentWindow > windowStart) {
                count.set(0);
                windowStart = currentWindow;
            }
            count.incrementAndGet();
        }

        int getCount() {
            long currentWindow = System.currentTimeMillis() / 60_000;
            if (currentWindow > windowStart) {
                return 0;
            }
            return count.get();
        }
    }
}

package com.songzy.dicserver.service;

import com.songzy.dicserver.exception.BusinessException;
import com.songzy.dicserver.model.AppUser;
import com.songzy.dicserver.model.RefreshSession;
import com.songzy.dicserver.model.UserStatus;
import com.songzy.dicserver.repository.AppUserRepository;
import com.songzy.dicserver.repository.RefreshSessionRepository;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RefreshSessionServiceConcurrencyTest {

    @Autowired
    private RefreshSessionService refreshSessionService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private RefreshSessionRepository refreshSessionRepository;

    @AfterEach
    void tearDown() {
        refreshSessionRepository.deleteAll();
        appUserRepository.deleteAll();
    }

    @Test
    void rotateSessionShouldAllowOnlyOneConcurrentSuccessPerToken() throws Exception {
        AppUser user = new AppUser();
        user.setUsername("alice");
        user.setPasswordHash("encoded-secret");
        user.setStatus(UserStatus.ACTIVE);
        AppUser savedUser = appUserRepository.saveAndFlush(user);
        RefreshSessionService.IssuedRefreshToken issuedRefreshToken = refreshSessionService.createSession(savedUser);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            Callable<RotationOutcome> task = () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start rotation");
                }
                try {
                    RefreshSessionService.IssuedRefreshToken rotated =
                            refreshSessionService.rotateSession(issuedRefreshToken.refreshToken());
                    return RotationOutcome.success(rotated.refreshToken());
                } catch (BusinessException e) {
                    return RotationOutcome.failure(e.getCode());
                }
            };

            Future<RotationOutcome> firstAttempt = executorService.submit(task);
            Future<RotationOutcome> secondAttempt = executorService.submit(task);

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<RotationOutcome> outcomes = List.of(getOutcome(firstAttempt), getOutcome(secondAttempt));

            assertThat(outcomes).filteredOn(RotationOutcome::success).hasSize(1);
            assertThat(outcomes)
                    .filteredOn(outcome -> !outcome.success())
                    .extracting(RotationOutcome::errorCode)
                    .containsExactly("REFRESH_TOKEN_INVALID");

            List<RefreshSession> sessions = refreshSessionRepository.findAll();
            assertThat(sessions).hasSize(2);
            assertThat(sessions).filteredOn(session -> session.getRevokedAt() == null).hasSize(1);
            assertThat(sessions).filteredOn(session -> session.getRevokedAt() != null).hasSize(1);
        } finally {
            executorService.shutdownNow();
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private RotationOutcome getOutcome(Future<RotationOutcome> future)
            throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException {
        return future.get(5, TimeUnit.SECONDS);
    }

    private record RotationOutcome(boolean success, String refreshToken, String errorCode) {

        private static RotationOutcome success(String refreshToken) {
            return new RotationOutcome(true, refreshToken, null);
        }

        private static RotationOutcome failure(String errorCode) {
            return new RotationOutcome(false, null, errorCode);
        }
    }
}

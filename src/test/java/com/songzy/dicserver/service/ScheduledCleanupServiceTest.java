package com.songzy.dicserver.service;

import com.songzy.dicserver.repository.QueryHistoryEventRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledCleanupServiceTest {

    @Mock
    private QueryHistoryEventRepository queryHistoryEventRepository;

    @Test
    void shouldPreferNewQueryHistoryRetentionProperty() {
        ScheduledCleanupService cleanupService = new ScheduledCleanupService(
                queryHistoryEventRepository,
                new MockEnvironment()
                        .withProperty("cleanup.query-history.retention-days", "30")
                        .withProperty("cleanup.search-history.retention-days", "45")
        );

        long retentionDays = (long) ReflectionTestUtils.getField(cleanupService, "queryHistoryRetentionDays");

        org.assertj.core.api.Assertions.assertThat(retentionDays).isEqualTo(30L);
    }

    @Test
    void shouldFallbackToLegacySearchHistoryRetentionProperty() {
        ScheduledCleanupService cleanupService = new ScheduledCleanupService(
                queryHistoryEventRepository,
                new MockEnvironment()
                        .withProperty("cleanup.search-history.retention-days", "45")
        );

        long retentionDays = (long) ReflectionTestUtils.getField(cleanupService, "queryHistoryRetentionDays");

        org.assertj.core.api.Assertions.assertThat(retentionDays).isEqualTo(45L);
    }

    @Test
    void cleanupShouldDeleteOldRecords() {
        ScheduledCleanupService cleanupService = new ScheduledCleanupService(
                queryHistoryEventRepository,
                new MockEnvironment().withProperty("cleanup.query-history.retention-days", "90")
        );
        when(queryHistoryEventRepository.deleteByCreatedAtBefore(any(LocalDateTime.class))).thenReturn(15);

        cleanupService.cleanupOldQueryHistory();

        verify(queryHistoryEventRepository).deleteByCreatedAtBefore(any(LocalDateTime.class));
    }

    @Test
    void cleanupShouldHandleRepositoryFailure() {
        ScheduledCleanupService cleanupService = new ScheduledCleanupService(
                queryHistoryEventRepository,
                new MockEnvironment().withProperty("cleanup.query-history.retention-days", "90")
        );
        when(queryHistoryEventRepository.deleteByCreatedAtBefore(any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("DB error"));

        // Should not throw
        cleanupService.cleanupOldQueryHistory();
    }
}

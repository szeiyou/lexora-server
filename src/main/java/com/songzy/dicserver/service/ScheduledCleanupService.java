package com.songzy.dicserver.service;

import com.songzy.dicserver.repository.QueryHistoryEventRepository;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduledCleanupService {
    private static final Logger logger = LoggerFactory.getLogger(ScheduledCleanupService.class);
    private static final String QUERY_HISTORY_RETENTION_PROPERTY = "cleanup.query-history.retention-days";
    private static final String LEGACY_SEARCH_HISTORY_RETENTION_PROPERTY = "cleanup.search-history.retention-days";
    private static final long DEFAULT_RETENTION_DAYS = 90L;

    private final QueryHistoryEventRepository queryHistoryEventRepository;
    private final long queryHistoryRetentionDays;

    public ScheduledCleanupService(QueryHistoryEventRepository queryHistoryEventRepository, Environment environment) {
        this.queryHistoryEventRepository = queryHistoryEventRepository;
        this.queryHistoryRetentionDays = resolveRetentionDays(environment);
        if (!environment.containsProperty(QUERY_HISTORY_RETENTION_PROPERTY)
                && environment.containsProperty(LEGACY_SEARCH_HISTORY_RETENTION_PROPERTY)) {
            logger.warn("配置项 {} 已废弃，请迁移到 {}",
                    LEGACY_SEARCH_HISTORY_RETENTION_PROPERTY, QUERY_HISTORY_RETENTION_PROPERTY);
        }
    }

    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupOldQueryHistory() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(queryHistoryRetentionDays);
        try {
            int deleted = queryHistoryEventRepository.deleteByCreatedAtBefore(cutoff);
            logger.info("查询历史清理完成: 删除了 {} 条超过 {} 天的记录", deleted, queryHistoryRetentionDays);
        } catch (Exception e) {
            logger.error("查询历史清理失败: {}", e.getMessage(), e);
        }
    }

    private static long resolveRetentionDays(Environment environment) {
        String configuredValue = environment.getProperty(QUERY_HISTORY_RETENTION_PROPERTY);
        if (configuredValue == null || configuredValue.isBlank()) {
            configuredValue = environment.getProperty(LEGACY_SEARCH_HISTORY_RETENTION_PROPERTY);
        }
        if (configuredValue == null || configuredValue.isBlank()) {
            return DEFAULT_RETENTION_DAYS;
        }
        try {
            return Long.parseLong(configuredValue);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("无效的查询历史清理保留天数配置: " + configuredValue, e);
        }
    }
}

package com.songzy.dicserver.dto;

import com.songzy.dicserver.model.EntryResultType;

import java.time.LocalDateTime;
import java.util.List;

public record QueryHistorySummaryDTO(
        String historyKey,
        String query,
        String normalizedQuery,
        EntryResultType resultType,
        String summary,
        LocalDateTime latestSearchTime,
        long searchCount,
        List<LocalDateTime> searchTimes
) {
    public QueryHistorySummaryDTO(
            String query,
            String normalizedQuery,
            EntryResultType resultType,
            String summary,
            LocalDateTime latestSearchTime,
            long searchCount,
            List<LocalDateTime> searchTimes
    ) {
        this(null, query, normalizedQuery, resultType, summary, latestSearchTime, searchCount, searchTimes);
    }
}

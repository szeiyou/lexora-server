package com.songzy.dicserver.dto;

import com.songzy.dicserver.model.EntryResultType;
import java.time.LocalDateTime;
import java.util.List;

public record QueryHistoryDetailDTO(
        String historyKey,
        EntryResultType resultType,
        String query,
        String normalizedQuery,
        LocalDateTime latestSearchTime,
        long searchCount,
        List<LocalDateTime> searchTimes,
        Object response
) {
}

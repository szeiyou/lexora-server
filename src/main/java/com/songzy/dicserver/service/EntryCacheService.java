package com.songzy.dicserver.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.songzy.dicserver.model.EntryQueryResponse;
import com.songzy.dicserver.model.EntryResultType;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class EntryCacheService {

    private static final String SENTENCE_TRANSLATION_ANALYSIS_VERSION = "v2";

    private final Cache<String, EntryQueryResponse> cache = Caffeine.newBuilder()
            .maximumSize(5000)
            .build();

    public Optional<EntryQueryResponse> get(String cacheKey) {
        return Optional.ofNullable(cache.getIfPresent(cacheKey));
    }

    public void put(String cacheKey, EntryQueryResponse response) {
        if (response != null) {
            cache.put(cacheKey, response);
        }
    }

    public void invalidate(String cacheKey) {
        if (cacheKey != null) {
            cache.invalidate(cacheKey);
        }
    }

    public String buildCacheKey(EntryResultType type, String normalizedQuery) {
        if (type == EntryResultType.TEXT_TRANSLATION) {
            throw new IllegalArgumentException("EntryCacheService does not support TEXT_TRANSLATION");
        }
        String cacheQuery = normalizedQuery == null ? "" : normalizedQuery.trim().replaceAll("\\s+", " ");
        if (type == EntryResultType.ENGLISH_WORD) {
            cacheQuery = cacheQuery.toLowerCase(Locale.ROOT);
        }
        if (type == EntryResultType.SENTENCE_TRANSLATION) {
            return type.name() + ":" + SENTENCE_TRANSLATION_ANALYSIS_VERSION + ":" + cacheQuery;
        }
        return type.name() + ":" + cacheQuery;
    }
}

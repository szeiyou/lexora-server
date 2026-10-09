package com.songzy.dicserver.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.songzy.dicserver.model.TextTranslationResponse;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class TextTranslationCacheService {

    private final Cache<String, TextTranslationResponse> cache = Caffeine.newBuilder()
            .maximumSize(5000)
            .build();

    public Optional<TextTranslationResponse> get(String cacheKey) {
        return Optional.ofNullable(cache.getIfPresent(cacheKey));
    }

    public void put(String cacheKey, TextTranslationResponse response) {
        if (response != null) {
            cache.put(cacheKey, response);
        }
    }

    public void invalidate(String cacheKey) {
        if (cacheKey != null) {
            cache.invalidate(cacheKey);
        }
    }
}

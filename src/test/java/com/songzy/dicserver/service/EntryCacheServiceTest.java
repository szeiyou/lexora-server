package com.songzy.dicserver.service;

import com.songzy.dicserver.model.EntryResultType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntryCacheServiceTest {

    private final EntryCacheService entryCacheService = new EntryCacheService();

    @Test
    void buildCacheKeyShouldVersionSentenceTranslationEntries() {
        assertThat(entryCacheService.buildCacheKey(EntryResultType.SENTENCE_TRANSLATION, "  你 好  "))
                .isEqualTo("SENTENCE_TRANSLATION:v2:你 好");
    }

    @Test
    void buildCacheKeyShouldRejectTextTranslationType() {
        assertThatThrownBy(() -> entryCacheService.buildCacheKey(EntryResultType.TEXT_TRANSLATION, "你好"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TEXT_TRANSLATION");
    }
}

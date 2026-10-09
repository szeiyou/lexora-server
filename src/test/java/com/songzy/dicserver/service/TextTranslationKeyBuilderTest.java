package com.songzy.dicserver.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextTranslationKeyBuilderTest {

    private final TextTranslationKeyBuilder keyBuilder = new TextTranslationKeyBuilder();

    @Test
    void normalizeTextForStorageShouldTrimCollapseWhitespaceAndApplyNfc() {
        assertThat(keyBuilder.normalizeTextForStorage("  cafe\u0301   AU   lait  "))
                .isEqualTo("café AU lait");
    }

    @Test
    void buildDedupeKeyShouldUseFixedLengthHashedIdentity() {
        String dedupeKey = keyBuilder.buildDedupeKey("第一句。第二句。", "zh", "en");

        assertThat(dedupeKey)
                .startsWith("text_translation:v1:")
                .hasSize("text_translation:v1:".length() + 64);
        assertThat(dedupeKey).doesNotContain("第一句");
    }

    @Test
    void buildKeysShouldKeepTranslationDirectionInHashInput() {
        String zhToEnDedupeKey = keyBuilder.buildDedupeKey("你好", "zh", "en");
        String enToZhDedupeKey = keyBuilder.buildDedupeKey("你好", "en", "zh");
        String zhToEnCacheKey = keyBuilder.buildCacheKey("你好", "zh", "en");

        assertThat(zhToEnDedupeKey).isNotEqualTo(enToZhDedupeKey);
        assertThat(keyBuilder.buildDedupeKeyFingerprint("你好", "zh", "en"))
                .hasSize(64)
                .isNotEqualTo(keyBuilder.buildDedupeKeyFingerprint("你好", "en", "zh"));
        assertThat(zhToEnCacheKey)
                .startsWith("TEXT_TRANSLATION:v1:")
                .hasSize("TEXT_TRANSLATION:v1:".length() + 64);
    }
}

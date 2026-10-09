package com.songzy.dicserver.service;

import com.songzy.dicserver.model.EntryResultType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryTextNormalizerTest {

    private final QueryTextNormalizer normalizer = new QueryTextNormalizer();

    @Test
    void normalizeForStorageShouldTrimCollapseWhitespaceAndApplyNfc() {
        assertThat(normalizer.normalizeForStorage("  cafe\u0301   AU   lait  "))
                .isEqualTo("café AU lait");
    }

    @Test
    void normalizeForLookupShouldLowercaseEnglishLetters() {
        assertThat(normalizer.normalizeForLookup("  VISIBLE  "))
                .isEqualTo("visible");
    }

    @Test
    void buildDedupeKeyShouldUseLowercaseResultTypePrefixAndLookupKey() {
        assertThat(normalizer.buildDedupeKey(EntryResultType.ENGLISH_WORD, "  Visible  "))
                .isEqualTo("english_word:visible");
        assertThat(normalizer.buildDedupeKey(EntryResultType.SENTENCE_TRANSLATION, " How ARE   you "))
                .isEqualTo("sentence_translation:v2:how are you");
    }

    @Test
    void fingerprintForLookupShouldDifferentiateAccentVariants() {
        assertThat(normalizer.fingerprintForLookup("cafe"))
                .isNotEqualTo(normalizer.fingerprintForLookup("café"));
    }

    @Test
    void fingerprintForLookupShouldTreatCanonicalUnicodeEquivalentsAsEqual() {
        assertThat(normalizer.fingerprintForLookup("é"))
                .isEqualTo(normalizer.fingerprintForLookup("e\u0301"));
    }

    @Test
    void dedupeKeyFingerprintShouldDifferentiateResultTypesForSameQueryText() {
        assertThat(normalizer.fingerprintForDedupeKey(EntryResultType.ENGLISH_WORD, "apple"))
                .isNotEqualTo(normalizer.fingerprintForDedupeKey(EntryResultType.SENTENCE_TRANSLATION, "apple"));
    }

    @Test
    void buildDedupeKeyShouldRejectTextTranslationType() {
        assertThatThrownBy(() -> normalizer.buildDedupeKey(EntryResultType.TEXT_TRANSLATION, "你好"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TEXT_TRANSLATION");
    }
}

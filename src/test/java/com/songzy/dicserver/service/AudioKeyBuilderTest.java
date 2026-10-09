package com.songzy.dicserver.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AudioKeyBuilderTest {

    private final QueryTextNormalizer normalizer = new QueryTextNormalizer();
    private final AudioKeyBuilder audioKeyBuilder = new AudioKeyBuilder(normalizer);

    @Test
    void buildShouldNormalizeEnglishWordHeadwordKeys() {
        assertThat(audioKeyBuilder.build(AudioKeyBuilder.AudioKeyType.ENGLISH_WORD_HEADWORD, "  Hello   WORLD  "))
                .isEqualTo("english_word:headword:hello world");
    }

    @Test
    void buildShouldNormalizeEnglishWordFullReadingKeys() {
        assertThat(audioKeyBuilder.build(AudioKeyBuilder.AudioKeyType.ENGLISH_WORD_FULL_READING, "  Hello   WORLD  "))
                .isEqualTo("english_word:full_reading:hello world");
    }

    @Test
    void buildShouldNormalizeZhToEnTermHeadwordKeys() {
        assertThat(audioKeyBuilder.build(AudioKeyBuilder.AudioKeyType.ZH_TO_EN_TERM_HEADWORD, "  苹果  "))
                .isEqualTo("zh_to_en_term:headword:苹果");
    }

    @Test
    void buildShouldReplaceSlashWithWhitespaceToKeepAudioKeysPathSafe() {
        assertThat(audioKeyBuilder.build(AudioKeyBuilder.AudioKeyType.ENGLISH_WORD_HEADWORD, "  A/B  "))
                .isEqualTo("english_word:headword:a b");
    }

    @Test
    void buildShouldReplaceColonWithWhitespaceToKeepStructuralSeparatorsUnambiguous() {
        assertThat(audioKeyBuilder.build(AudioKeyBuilder.AudioKeyType.ENGLISH_WORD_HEADWORD, "  Rock:And:Roll  "))
                .isEqualTo("english_word:headword:rock and roll");
    }

    @Test
    void buildShouldNormalizeCanonicallyEquivalentUnicodeTextToSameKey() {
        assertThat(audioKeyBuilder.build(AudioKeyBuilder.AudioKeyType.ENGLISH_WORD_HEADWORD, "é"))
                .isEqualTo(audioKeyBuilder.build(AudioKeyBuilder.AudioKeyType.ENGLISH_WORD_HEADWORD, "e\u0301"));
    }

    @Test
    void buildShouldFollowSharedLookupNormalizerContract() {
        assertThat(audioKeyBuilder.build(AudioKeyBuilder.AudioKeyType.ENGLISH_WORD_HEADWORD, "  VISIBLE  "))
                .isEqualTo("english_word:headword:" + normalizer.normalizeForLookup("  VISIBLE  "));
    }

    @Test
    void normalizeAudioKeyShouldCanonicalizeEquivalentColonContainingKeys() {
        assertThat(AudioKeyBuilder.normalizeAudioKey("english_word:headword:rock:and:roll"))
                .isEqualTo(AudioKeyBuilder.normalizeAudioKey("english_word:headword:rock and roll"));
    }

    @Test
    void audioUrlShouldEncodeAudioKeyAsAPathSegment() {
        assertThat(audioKeyBuilder.audioUrl("english_word:full_reading:hello world"))
                .isEqualTo("/api/v1/audio/by-key/english_word%3Afull_reading%3Ahello%20world");
    }

    @Test
    void audioUrlShouldEncodePathSafeGeneratedAudioKey() {
        assertThat(audioKeyBuilder.audioUrl(audioKeyBuilder.build(AudioKeyBuilder.AudioKeyType.ENGLISH_WORD_HEADWORD, "A/B")))
                .isEqualTo("/api/v1/audio/by-key/english_word%3Aheadword%3Aa%20b");
    }
}

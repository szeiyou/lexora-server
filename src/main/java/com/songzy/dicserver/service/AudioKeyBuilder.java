package com.songzy.dicserver.service;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import static java.nio.charset.StandardCharsets.UTF_8;

@Component
public class AudioKeyBuilder {
    private static final QueryTextNormalizer STATIC_NORMALIZER = new QueryTextNormalizer();

    private final QueryTextNormalizer queryTextNormalizer;

    public AudioKeyBuilder() {
        this(STATIC_NORMALIZER);
    }

    public AudioKeyBuilder(QueryTextNormalizer queryTextNormalizer) {
        this.queryTextNormalizer = queryTextNormalizer;
    }

    public String build(AudioKeyType type, String text) {
        if (type == null) {
            return null;
        }

        String normalizedText = normalizeLookupText(text);
        if (normalizedText == null) {
            return null;
        }

        return type.prefix + ":" + normalizedText;
    }

    public String audioUrl(String audioKey) {
        if (audioKey == null || audioKey.trim().isEmpty()) {
            return null;
        }

        return "/api/v1/audio/by-key/" + UriUtils.encodePathSegment(audioKey, UTF_8).replace(":", "%3A");
    }

    public static String normalizeAudioKey(String audioKey) {
        if (audioKey == null) {
            return null;
        }

        String trimmed = audioKey.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        for (AudioKeyType type : AudioKeyType.values()) {
            String keyPrefix = type.prefix + ":";
            if (!trimmed.startsWith(keyPrefix)) {
                continue;
            }

            String normalizedText = normalizeText(trimmed.substring(keyPrefix.length()));
            if (normalizedText == null) {
                return null;
            }

            return keyPrefix + normalizedText;
        }

        int separatorIndex = trimmed.lastIndexOf(':');
        if (separatorIndex < 0) {
            return normalizeText(trimmed);
        }

        String normalizedText = normalizeText(trimmed.substring(separatorIndex + 1));
        if (normalizedText == null) {
            return null;
        }

        return trimmed.substring(0, separatorIndex + 1) + normalizedText;
    }

    static String normalizeText(String text) {
        return STATIC_NORMALIZER.normalizeForLookup(sanitizeForAudioKey(text));
    }

    private static String sanitizeForAudioKey(String text) {
        return text == null ? null : text.replace('/', ' ').replace(':', ' ');
    }

    private String normalizeLookupText(String text) {
        return queryTextNormalizer.normalizeForLookup(sanitizeForAudioKey(text));
    }

    public enum AudioKeyType {
        ENGLISH_WORD_HEADWORD("english_word:headword"),
        ENGLISH_WORD_FULL_READING("english_word:full_reading"),
        ZH_TO_EN_TERM_HEADWORD("zh_to_en_term:headword");

        private final String prefix;

        AudioKeyType(String prefix) {
            this.prefix = prefix;
        }
    }
}

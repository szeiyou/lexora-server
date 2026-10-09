package com.songzy.dicserver.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class TextTranslationKeyBuilder {

    private static final String DEDUPE_KEY_PREFIX = "text_translation:v1:";
    private static final String CACHE_KEY_PREFIX = "TEXT_TRANSLATION:v1:";

    public String normalizeTextForStorage(String rawText) {
        if (rawText == null) {
            return null;
        }

        String normalized = Normalizer.normalize(rawText, Normalizer.Form.NFC)
                .trim()
                .replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }

    public String buildDedupeKey(String normalizedText, String sourceLanguage, String targetLanguage) {
        String identityInput = buildIdentityInput(normalizedText, sourceLanguage, targetLanguage);
        if (identityInput == null) {
            return null;
        }
        return DEDUPE_KEY_PREFIX + sha256Hex(identityInput);
    }

    public String buildDedupeKeyFingerprint(String normalizedText, String sourceLanguage, String targetLanguage) {
        String dedupeKey = buildDedupeKey(normalizedText, sourceLanguage, targetLanguage);
        if (dedupeKey == null) {
            return null;
        }
        return sha256Hex(dedupeKey);
    }

    public String buildCacheKey(String normalizedText, String sourceLanguage, String targetLanguage) {
        String identityInput = buildIdentityInput(normalizedText, sourceLanguage, targetLanguage);
        if (identityInput == null) {
            return null;
        }
        return CACHE_KEY_PREFIX + sha256Hex(identityInput);
    }

    private String buildIdentityInput(String normalizedText, String sourceLanguage, String targetLanguage) {
        String normalizedStorageText = normalizeTextForStorage(normalizedText);
        String normalizedSourceLanguage = normalizeLanguage(sourceLanguage);
        String normalizedTargetLanguage = normalizeLanguage(targetLanguage);
        if (normalizedStorageText == null || normalizedSourceLanguage == null || normalizedTargetLanguage == null) {
            return null;
        }
        return normalizedStorageText + "|" + normalizedSourceLanguage + "|" + normalizedTargetLanguage;
    }

    private String normalizeLanguage(String language) {
        if (language == null) {
            return null;
        }
        String normalized = language.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }
}

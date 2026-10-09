package com.songzy.dicserver.service;

import com.songzy.dicserver.model.EntryResultType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class QueryTextNormalizer {

    private static final String SENTENCE_TRANSLATION_ANALYSIS_VERSION = "v2";

    public String normalizeForStorage(String rawQuery) {
        if (rawQuery == null) {
            return null;
        }

        String normalized = Normalizer.normalize(rawQuery, Normalizer.Form.NFC)
                .trim()
                .replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }

    public String normalizeForLookup(String rawQuery) {
        String storageNormalized = normalizeForStorage(rawQuery);
        return storageNormalized == null ? null : storageNormalized.toLowerCase(Locale.ROOT);
    }

    public String buildDedupeKey(EntryResultType resultType, String rawQuery) {
        if (resultType == null) {
            return null;
        }
        if (resultType == EntryResultType.TEXT_TRANSLATION) {
            throw new IllegalArgumentException("QueryTextNormalizer does not support TEXT_TRANSLATION");
        }
        String lookupKey = normalizeForLookup(rawQuery);
        if (lookupKey == null) {
            return null;
        }
        String resultTypePrefix = resultType.name().toLowerCase(Locale.ROOT);
        if (resultType == EntryResultType.SENTENCE_TRANSLATION) {
            return resultTypePrefix + ":" + SENTENCE_TRANSLATION_ANALYSIS_VERSION + ":" + lookupKey;
        }
        return resultTypePrefix + ":" + lookupKey;
    }

    public String fingerprintForLookup(String rawQuery) {
        String lookupKey = normalizeForLookup(rawQuery);
        if (lookupKey == null) {
            return null;
        }
        return sha256Hex(lookupKey);
    }

    public String fingerprintForDedupeKey(EntryResultType resultType, String rawQuery) {
        String dedupeKey = buildDedupeKey(resultType, rawQuery);
        if (dedupeKey == null) {
            return null;
        }
        return sha256Hex(dedupeKey);
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

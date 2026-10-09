package com.songzy.dicserver.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class JsonContentSanitizer {
    private static final Pattern MARKDOWN_CODE_FENCE =
            Pattern.compile("^```(?:json)?\\s*(.*?)\\s*```$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private JsonContentSanitizer() {
    }

    static String sanitize(String rawContent) {
        if (rawContent == null) {
            return null;
        }

        String sanitized = rawContent.strip();
        if (sanitized.isEmpty()) {
            return sanitized;
        }

        Matcher fenced = MARKDOWN_CODE_FENCE.matcher(sanitized);
        if (fenced.matches()) {
            sanitized = fenced.group(1).strip();
        }

        if (sanitized.startsWith("{") || sanitized.startsWith("[")) {
            return sanitized;
        }

        int firstObjectStart = sanitized.indexOf('{');
        int lastObjectEnd = sanitized.lastIndexOf('}');
        if (firstObjectStart >= 0 && lastObjectEnd > firstObjectStart) {
            return sanitized.substring(firstObjectStart, lastObjectEnd + 1).strip();
        }

        int firstArrayStart = sanitized.indexOf('[');
        int lastArrayEnd = sanitized.lastIndexOf(']');
        if (firstArrayStart >= 0 && lastArrayEnd > firstArrayStart) {
            return sanitized.substring(firstArrayStart, lastArrayEnd + 1).strip();
        }

        return sanitized;
    }

    static String abbreviate(String rawContent, int maxLength) {
        if (rawContent == null) {
            return "null";
        }

        String sanitized = rawContent.replaceAll("\\s+", " ").trim();
        if (sanitized.length() <= maxLength) {
            return sanitized;
        }
        return sanitized.substring(0, maxLength) + "...";
    }
}

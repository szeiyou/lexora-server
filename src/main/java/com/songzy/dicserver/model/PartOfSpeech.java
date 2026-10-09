package com.songzy.dicserver.model;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Normalizes dictionary labels, including labels read from older stored responses. */
public final class PartOfSpeech {
    private static final Map<String, String> ABBREVIATIONS = new LinkedHashMap<>();

    static {
        register("n.", "n", "noun", "名词", "countable noun", "uncountable noun", "可数名词", "不可数名词", "proper noun", "专有名词");
        register("v.", "v", "verb", "动词");
        register("vt.", "vt", "transitive verb", "及物动词");
        register("vi.", "vi", "intransitive verb", "不及物动词");
        register("adj.", "adj", "adjective", "形容词", "a", "a.");
        register("adv.", "adv", "adverb", "副词", "ad", "ad.");
        register("pron.", "pron", "pronoun", "代词");
        register("prep.", "prep", "preposition", "介词");
        register("conj.", "conj", "conjunction", "连词");
        register("interj.", "interj", "interjection", "感叹词", "叹词", "int", "int.");
        register("art.", "art", "article", "冠词");
        register("det.", "det", "determiner", "限定词");
        register("num.", "num", "numeral", "数词");
        register("aux.", "aux", "auxiliary verb", "auxiliary", "助动词", "modal verb", "modal", "情态动词");
    }

    private PartOfSpeech() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String trimmed = value.trim();
        String abbreviation = ABBREVIATIONS.get(trimmed.toLowerCase(Locale.ROOT));
        if (abbreviation != null) {
            return abbreviation;
        }

        Set<String> labels = new LinkedHashSet<>();
        for (String label : trimmed.split("(?i)\\s*(?:[,，、;；/&＆+]|\\band\\b|\\bor\\b|和|或|兼)\\s*|(?<=\\.)\\s+")) {
            if (!label.isBlank()) {
                // Preserve unfamiliar labels rather than guessing their grammatical meaning.
                labels.add(ABBREVIATIONS.getOrDefault(label.trim().toLowerCase(Locale.ROOT), label.trim()));
            }
        }
        return labels.isEmpty() ? trimmed : String.join(", ", labels);
    }

    private static void register(String abbreviation, String... aliases) {
        ABBREVIATIONS.put(abbreviation, abbreviation);
        for (String alias : aliases) {
            ABBREVIATIONS.put(alias, abbreviation);
        }
    }
}

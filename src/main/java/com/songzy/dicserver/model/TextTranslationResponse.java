package com.songzy.dicserver.model;

import java.util.List;
import lombok.Data;

@Data
public class TextTranslationResponse {
    private String text;
    private String normalizedText;
    private String sourceLanguage;
    private String targetLanguage;
    private String translatedText;
    private List<Segment> segments;
    private List<KeyPhrase> keyPhrases;
    private String notes;

    @Data
    public static class Segment {
        private String sourceText;
        private String translatedText;
    }

    @Data
    public static class KeyPhrase {
        private String sourceText;
        private String translatedText;
        private String note;
    }
}

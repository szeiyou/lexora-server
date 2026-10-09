package com.songzy.dicserver.model;

import java.util.List;
import lombok.Data;

@Data
public class EntryQueryResponse {
    private String query;
    private String normalizedQuery;
    private EntryResultType resultType;
    private String sourceLanguage;
    private String targetLanguage;
    private EnglishWordPayload englishWord;
    private ZhToEnTermPayload zhToEnTerm;
    private SentenceTranslationPayload sentenceTranslation;

    @Data
    public static class EnglishWordPayload {
        private String word;
        private Pronunciation pronunciation;
        private List<Definition> definitions;
        private List<Example> examples;
        private String etymology;
        private String extension;
        private HeadwordAudio headwordAudio;
        private HeadwordAudio fullReadingAudio;
    }

    @Data
    public static class ZhToEnTermPayload {
        private String input;
        private String usageTip;
        private List<TermCandidate> candidates;
    }

    @Data
    public static class SentenceTranslationPayload {
        private String sourceSentence;
        private String translatedSentence;
        private String literalTranslation;
        private String grammarBreakdown;
        private List<KeyPhrase> keyPhrases;
        private List<String> alternativeExpressions;
        private String learningNotes;
    }

    @Data
    public static class Pronunciation {
        private String uk;
        private String us;
    }

    @Data
    public static class Definition {
        private String partOfSpeech;
        private String meaning;
        private List<String> synonyms;

        public void setPartOfSpeech(String partOfSpeech) {
            this.partOfSpeech = PartOfSpeech.normalize(partOfSpeech);
        }

    }

    @Data
    public static class Example {
        private String sentence;
        private String translation;
    }

    @Data
    public static class HeadwordAudio {
        private String audioKey;
        private String audioUrl;
    }

    @Data
    public static class TermCandidate {
        private String term;
        private Pronunciation pronunciation;
        private String partOfSpeech;
        private String coreMeaning;
        private String usageContext;
        private String difference;
        private String example;
        private String exampleTranslation;
        private HeadwordAudio headwordAudio;

        public void setPartOfSpeech(String partOfSpeech) {
            this.partOfSpeech = PartOfSpeech.normalize(partOfSpeech);
        }

    }

    @Data
    public static class KeyPhrase {
        private String phrase;
        private String explanation;
    }
}

package com.songzy.dicserver.model;

import java.util.List;
import lombok.Data;

@Data
public class WordResponse {
    private String word;
    private Pronunciation pronunciation;
    private List<Definition> definitions;
    private List<Example> examples;
    private String etymology;
    private String extension;

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
}

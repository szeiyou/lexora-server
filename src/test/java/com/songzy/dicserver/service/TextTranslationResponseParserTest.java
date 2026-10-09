package com.songzy.dicserver.service;

import com.songzy.dicserver.exception.BusinessException;
import com.songzy.dicserver.model.TextTranslationResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextTranslationResponseParserTest {

    private final TextTranslationResponseParser parser = new TextTranslationResponseParser();

    @Test
    void parseShouldReturnTranslationWithSegmentsAndKeyPhrases() {
        String rawJson = """
                {
                  "text": "第一句。第二句。",
                  "normalizedText": "第一句。第二句。",
                  "sourceLanguage": "zh",
                  "targetLanguage": "en",
                  "translatedText": "Sentence one. Sentence two.",
                  "segments": [
                    {
                      "sourceText": "第一句。",
                      "translatedText": "Sentence one."
                    },
                    {
                      "sourceText": "第二句。",
                      "translatedText": "Sentence two."
                    }
                  ],
                  "keyPhrases": [
                    {
                      "sourceText": "第一句",
                      "translatedText": "Sentence one",
                      "note": "保持简单直译。"
                    }
                  ],
                  "notes": "整体采用自然表达。"
                }
                """;

        TextTranslationResponse response = parser.parse(rawJson);

        assertThat(response.getText()).isEqualTo("第一句。第二句。");
        assertThat(response.getNormalizedText()).isEqualTo("第一句。第二句。");
        assertThat(response.getSourceLanguage()).isEqualTo("zh");
        assertThat(response.getTargetLanguage()).isEqualTo("en");
        assertThat(response.getTranslatedText()).isEqualTo("Sentence one. Sentence two.");
        assertThat(response.getSegments()).hasSize(2);
        assertThat(response.getSegments().get(0).getSourceText()).isEqualTo("第一句。");
        assertThat(response.getSegments().get(0).getTranslatedText()).isEqualTo("Sentence one.");
        assertThat(response.getSegments().get(1).getSourceText()).isEqualTo("第二句。");
        assertThat(response.getSegments().get(1).getTranslatedText()).isEqualTo("Sentence two.");
        assertThat(response.getKeyPhrases()).hasSize(1);
        assertThat(response.getKeyPhrases().getFirst().getSourceText()).isEqualTo("第一句");
        assertThat(response.getKeyPhrases().getFirst().getTranslatedText()).isEqualTo("Sentence one");
        assertThat(response.getKeyPhrases().getFirst().getNote()).isEqualTo("保持简单直译。");
        assertThat(response.getNotes()).isEqualTo("整体采用自然表达。");
    }

    @Test
    void parseShouldThrowWhenTranslatedTextIsMissing() {
        String rawJson = """
                {
                  "text": "第一句。",
                  "normalizedText": "第一句。",
                  "sourceLanguage": "zh",
                  "targetLanguage": "en",
                  "segments": [
                    {
                      "sourceText": "第一句。",
                      "translatedText": "Sentence one."
                    }
                  ],
                  "keyPhrases": [],
                  "notes": ""
                }
                """;

        assertThatThrownBy(() -> parser.parse(rawJson))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("LLM_RESPONSE_INVALID");
    }

    @Test
    void parseShouldThrowWhenSegmentsIsMissing() {
        String rawJson = """
                {
                  "text": "第一句。",
                  "normalizedText": "第一句。",
                  "sourceLanguage": "zh",
                  "targetLanguage": "en",
                  "translatedText": "Sentence one.",
                  "keyPhrases": [],
                  "notes": ""
                }
                """;

        assertThatThrownBy(() -> parser.parse(rawJson))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("LLM_RESPONSE_INVALID");
    }

    @Test
    void parseShouldThrowWhenKeyPhraseItemIsMalformed() {
        String rawJson = """
                {
                  "text": "第一句。",
                  "normalizedText": "第一句。",
                  "sourceLanguage": "zh",
                  "targetLanguage": "en",
                  "translatedText": "Sentence one.",
                  "segments": [
                    {
                      "sourceText": "第一句。",
                      "translatedText": "Sentence one."
                    }
                  ],
                  "keyPhrases": [
                    {
                      "sourceText": "第一句",
                      "note": "缺少译文。"
                    }
                  ],
                  "notes": ""
                }
                """;

        assertThatThrownBy(() -> parser.parse(rawJson))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("LLM_RESPONSE_INVALID");
    }
}

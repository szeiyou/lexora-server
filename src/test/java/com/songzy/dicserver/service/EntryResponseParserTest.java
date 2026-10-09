package com.songzy.dicserver.service;

import com.songzy.dicserver.exception.BusinessException;
import com.songzy.dicserver.model.EntryQueryResponse;
import com.songzy.dicserver.model.EntryResultType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntryResponseParserTest {

    private final EntryResponseParser parser = new EntryResponseParser();

    @Test
    void parseShouldReturnEnglishWordPayloadWhenRequestedTypeMatches() {
        String rawJson = """
                {
                  "query": "phenomenon",
                  "normalizedQuery": "phenomenon",
                  "resultType": "ENGLISH_WORD",
                  "sourceLanguage": "en",
                  "targetLanguage": "zh",
                  "payload": {
                    "word": "phenomenon",
                    "pronunciation": {
                      "uk": "/fəˈnɒm.ɪ.nən/",
                      "us": "/fəˈnɑː.mə.nɑːn/"
                    },
                    "definitions": [
                      {
                        "partOfSpeech": "n.",
                        "meaning": "现象",
                        "synonyms": ["occurrence"]
                      }
                    ],
                    "examples": [
                      {
                        "sentence": "The northern lights are a natural phenomenon.",
                        "translation": "北极光是一种自然现象。"
                      }
                    ],
                    "etymology": "From Greek.",
                    "extension": "可用于描述自然或社会现象。"
                  }
                }
                """;

        EntryQueryResponse response = parser.parse(rawJson, EntryResultType.ENGLISH_WORD);

        assertThat(response.getResultType()).isEqualTo(EntryResultType.ENGLISH_WORD);
        assertThat(response.getQuery()).isEqualTo("phenomenon");
        assertThat(response.getEnglishWord()).isNotNull();
        assertThat(response.getEnglishWord().getWord()).isEqualTo("phenomenon");
        assertThat(response.getEnglishWord().getPronunciation().getUk()).isEqualTo("/fəˈnɒm.ɪ.nən/");
        assertThat(response.getEnglishWord().getDefinitions()).hasSize(1);
        assertThat(response.getEnglishWord().getExamples()).hasSize(1);
        assertThat(response.getEnglishWord().getHeadwordAudio()).isNull();
        assertThat(response.getZhToEnTerm()).isNull();
        assertThat(response.getSentenceTranslation()).isNull();
    }

    @Test
    void parseShouldAcceptMarkdownCodeFenceWrappedJson() {
        String rawJson = """
                ```json
                {
                  "query": "visible",
                  "normalizedQuery": "visible",
                  "resultType": "ENGLISH_WORD",
                  "sourceLanguage": "en",
                  "targetLanguage": "zh",
                  "payload": {
                    "word": "visible",
                    "pronunciation": {
                      "uk": "/ˈvɪz.ə.bəl/",
                      "us": "/ˈvɪz.ə.bəl/"
                    },
                    "definitions": [
                      {
                        "partOfSpeech": "adjective",
                        "meaning": "可见的",
                        "synonyms": ["noticeable"]
                      }
                    ],
                    "examples": [],
                    "etymology": "",
                    "extension": ""
                  }
                }
                ```
                """;

        EntryQueryResponse response = parser.parse(rawJson, EntryResultType.ENGLISH_WORD);

        assertThat(response.getResultType()).isEqualTo(EntryResultType.ENGLISH_WORD);
        assertThat(response.getEnglishWord().getWord()).isEqualTo("visible");
        assertThat(response.getEnglishWord().getDefinitions().getFirst().getPartOfSpeech()).isEqualTo("adj.");
    }

    @Test
    void parseShouldReturnZhToEnTermPayloadWhenRequestedTypeMatches() {
        String rawJson = """
                {
                  "query": "苹果",
                  "normalizedQuery": "苹果",
                  "resultType": "ZH_TO_EN_TERM",
                  "sourceLanguage": "zh",
                  "targetLanguage": "en",
                  "payload": {
                    "input": "苹果",
                    "usageTip": "水果通常用 apple；公司名称用 Apple。",
                    "candidates": [
                      {
                        "term": "apple",
                        "pronunciation": {
                          "uk": "/ˈæp.əl/",
                          "us": "/ˈæp.əl/"
                        },
                        "partOfSpeech": "n.",
                        "coreMeaning": "苹果",
                        "usageContext": "日常指水果时使用",
                        "difference": "最常见、最自然的表达。",
                        "example": "I eat an apple every morning.",
                        "exampleTranslation": "我每天早上吃一个苹果。",
                        "headwordAudio": {
                          "audioKey": "zh_to_en_term:apple",
                          "audioUrl": "/api/v1/audio/by-key/zh_to_en_term%3Aapple"
                        }
                      }
                    ]
                  }
                }
                """;

        EntryQueryResponse response = parser.parse(rawJson, EntryResultType.ZH_TO_EN_TERM);

        assertThat(response.getResultType()).isEqualTo(EntryResultType.ZH_TO_EN_TERM);
        assertThat(response.getZhToEnTerm()).isNotNull();
        assertThat(response.getZhToEnTerm().getInput()).isEqualTo("苹果");
        assertThat(response.getZhToEnTerm().getCandidates()).hasSize(1);
        assertThat(response.getZhToEnTerm().getCandidates().getFirst().getTerm()).isEqualTo("apple");
        assertThat(response.getZhToEnTerm().getCandidates().getFirst().getHeadwordAudio()).isNotNull();
        assertThat(response.getZhToEnTerm().getCandidates().getFirst().getHeadwordAudio().getAudioKey())
                .isEqualTo("zh_to_en_term:apple");
        assertThat(response.getEnglishWord()).isNull();
        assertThat(response.getSentenceTranslation()).isNull();
    }

    @Test
    void parseShouldTreatHeadwordAudioAsOptionalInZhToEnTermCandidates() {
        String rawJson = """
                {
                  "query": "苹果",
                  "normalizedQuery": "苹果",
                  "resultType": "ZH_TO_EN_TERM",
                  "sourceLanguage": "zh",
                  "targetLanguage": "en",
                  "payload": {
                    "input": "苹果",
                    "usageTip": "水果通常用 apple。",
                    "candidates": [
                      {
                        "term": "apple",
                        "pronunciation": {
                          "uk": "/ˈæp.əl/",
                          "us": "/ˈæp.əl/"
                        },
                        "partOfSpeech": "n.",
                        "coreMeaning": "苹果",
                        "usageContext": "日常指水果时使用",
                        "difference": "最常见的表达。",
                        "example": "I ate an apple.",
                        "exampleTranslation": "我吃了一个苹果。"
                      }
                    ]
                  }
                }
                """;

        EntryQueryResponse response = parser.parse(rawJson, EntryResultType.ZH_TO_EN_TERM);

        assertThat(response.getZhToEnTerm().getCandidates()).hasSize(1);
        assertThat(response.getZhToEnTerm().getCandidates().getFirst().getHeadwordAudio()).isNull();
    }

    @Test
    void parseShouldReturnSentenceTranslationPayloadWhenRequestedTypeMatches() {
        String rawJson = """
                {
                  "query": "你今天怎么样？",
                  "normalizedQuery": "你今天怎么样？",
                  "resultType": "SENTENCE_TRANSLATION",
                  "sourceLanguage": "zh",
                  "targetLanguage": "en",
                  "payload": {
                    "sourceSentence": "你今天怎么样？",
                    "translatedSentence": "How are you today?",
                    "literalTranslation": "How are you today?",
                    "grammarBreakdown": "How are you today 是常见问候语。",
                    "keyPhrases": [
                      {
                        "phrase": "How are you",
                        "explanation": "用于询问对方近况。"
                      }
                    ],
                    "alternativeExpressions": [
                      "How have you been today?"
                    ],
                    "learningNotes": "可用于日常打招呼。"
                  }
                }
                """;

        EntryQueryResponse response = parser.parse(rawJson, EntryResultType.SENTENCE_TRANSLATION);

        assertThat(response.getResultType()).isEqualTo(EntryResultType.SENTENCE_TRANSLATION);
        assertThat(response.getSentenceTranslation()).isNotNull();
        assertThat(response.getSentenceTranslation().getSourceSentence()).isEqualTo("你今天怎么样？");
        assertThat(response.getSentenceTranslation().getTranslatedSentence()).isEqualTo("How are you today?");
        assertThat(response.getSentenceTranslation().getKeyPhrases()).hasSize(1);
        assertThat(response.getSentenceTranslation().getAlternativeExpressions())
                .containsExactly("How have you been today?");
        assertThat(response.getEnglishWord()).isNull();
        assertThat(response.getZhToEnTerm()).isNull();
    }

    @Test
    void parseShouldThrowWhenRequestedTypeDoesNotMatchReturnedType() {
        String rawJson = """
                {
                  "query": "apple",
                  "normalizedQuery": "apple",
                  "resultType": "ENGLISH_WORD",
                  "sourceLanguage": "en",
                  "targetLanguage": "zh",
                  "payload": {
                    "word": "apple",
                    "pronunciation": {
                      "uk": "/ˈæp.əl/",
                      "us": "/ˈæp.əl/"
                    },
                    "definitions": [],
                    "examples": [],
                    "etymology": "",
                    "extension": ""
                  }
                }
                """;

        assertThatThrownBy(() -> parser.parse(rawJson, EntryResultType.ZH_TO_EN_TERM))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("LLM_RESPONSE_INVALID");
    }

    @Test
    void parseShouldThrowWhenOuterWrapperIsMalformed() {
        String rawJson = """
                {
                  "query": "apple",
                  "resultType": "ENGLISH_WORD",
                  "payload": {}
                }
                """;

        assertThatThrownBy(() -> parser.parse(rawJson, EntryResultType.ENGLISH_WORD))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("LLM_RESPONSE_INVALID");
    }

    @Test
    void parseShouldRejectTextTranslationRequestedType() {
        String rawJson = """
                {
                  "query": "apple",
                  "normalizedQuery": "apple",
                  "resultType": "ENGLISH_WORD",
                  "sourceLanguage": "en",
                  "targetLanguage": "zh",
                  "payload": {
                    "word": "apple",
                    "pronunciation": {
                      "uk": "/ˈæp.əl/",
                      "us": "/ˈæp.əl/"
                    },
                    "definitions": [],
                    "examples": [],
                    "etymology": "",
                    "extension": ""
                  }
                }
                """;

        assertThatThrownBy(() -> parser.parse(rawJson, EntryResultType.TEXT_TRANSLATION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TEXT_TRANSLATION");
    }
}

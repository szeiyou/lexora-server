package com.songzy.dicserver.service;

import com.songzy.dicserver.model.EntryResultType;
import org.springframework.stereotype.Component;

@Component
public class EntryPromptBuilder {

    private static final String BASE_ROLE_AND_OUTPUT_RULES = """
            ROLE & OUTPUT RULES
            - You are a bilingual English-Chinese teacher helping Chinese learners.
            - Return only valid JSON. Do not output Markdown.
            - Every response must use this outer wrapper:
              {
                "query": "...",
                "normalizedQuery": "...",
                "resultType": "...",
                "sourceLanguage": "zh or en",
                "targetLanguage": "zh or en",
                "payload": {}
              }
            - Keep the wrapper fields exactly as shown above.
            - Use empty strings or empty arrays instead of omitting required fields.
            """;

    private static final String COMMON_LANGUAGE_RULES = """
            COMMON LANGUAGE RULES
            - All learner-facing explanations and descriptive fields must be written in Simplified Chinese unless the field itself is an English example, English term, pronunciation value, or partOfSpeech label.
            - Every partOfSpeech field must use English dictionary abbreviations only: n., v., vt., vi., adj., adv., pron., prep., conj., interj., art., det., num., aux.
            - Never use full English labels such as noun or verb, or Chinese labels such as 名词 or 动词, in partOfSpeech.
            - Separate multiple partOfSpeech labels with a comma and space, for example "n., v.".
            - Prefer direct, practical explanations for Chinese learners instead of abstract dictionary prose.
            - Keep the tone specific and useful. Do not write empty praise or filler.
            """;

    private static final String ENGLISH_WORD_SCHEMA = """
            ENGLISH_WORD payload schema:
            {
              "word": "...",
              "pronunciation": { "uk": "...", "us": "..." },
              "definitions": [
                {
                  "partOfSpeech": "...",
                  "meaning": "...",
                  "synonyms": ["..."]
                }
              ],
              "examples": [
                {
                  "sentence": "...",
                  "translation": "..."
                }
              ],
              "etymology": "...",
              "extension": "..."
            }
            """;

    private static final String ZH_TO_EN_SCHEMA = """
            ZH_TO_EN_TERM payload schema:
            {
              "input": "...",
              "usageTip": "...",
              "candidates": [
                {
                  "term": "...",
                  "pronunciation": { "uk": "...", "us": "..." },
                  "partOfSpeech": "...",
                  "coreMeaning": "...",
                  "usageContext": "...",
                  "difference": "...",
                  "example": "...",
                  "exampleTranslation": "..."
                }
              ]
            }
            """;

    private static final String SENTENCE_TRANSLATION_SCHEMA = """
            SENTENCE_TRANSLATION payload schema:
            {
              "sourceSentence": "...",
              "translatedSentence": "...",
              "literalTranslation": "...",
              "grammarBreakdown": "...",
              "keyPhrases": [
                {
                  "phrase": "...",
                  "explanation": "..."
                }
              ],
              "alternativeExpressions": ["..."],
              "learningNotes": "..."
            }
            """;

    public String buildPrompt(EntryResultType requestedType) {
        rejectTextTranslation(requestedType);

        StringBuilder prompt = new StringBuilder();
        appendSection(prompt, roleAndOutputRules(requestedType));
        appendSection(prompt, COMMON_LANGUAGE_RULES);
        appendSection(prompt, resultTypeSpecificQualityRules(requestedType));
        appendSection(prompt, jsonStructureRequirements(requestedType));
        return prompt.toString();
    }

    private String roleAndOutputRules(EntryResultType requestedType) {
        StringBuilder rules = new StringBuilder(BASE_ROLE_AND_OUTPUT_RULES.trim());

        if (requestedType != null) {
            rules.append("\n- The requested resultType is ").append(requestedType).append(".\n")
                    .append("- Do not change the resultType.\n")
                    .append("- Output only the matching payload schema.");
            return rules.toString();
        }

        rules.append("""

                - First classify the input into ENGLISH_WORD, ZH_TO_EN_TERM, or SENTENCE_TRANSLATION.
                - Then output the matching resultType and payload schema.
                """);
        return rules.toString();
    }

    private String resultTypeSpecificQualityRules(EntryResultType requestedType) {
        StringBuilder rules = new StringBuilder("RESULT-TYPE SPECIFIC QUALITY RULES\n");

        if (requestedType == null || requestedType == EntryResultType.ENGLISH_WORD) {
            rules.append("""
                    ENGLISH_WORD:
                    - definitions[].meaning must be Simplified Chinese and must be a Chinese gloss, not an English explanation.
                    - etymology must be Simplified Chinese.
                    - examples[].translation must be Simplified Chinese.
                    - extension must be Simplified Chinese.
                    - extension must first explain practical usage, register, boundaries, or common mistakes.
                    - extension must then add common collocations or typical contexts.
                    - extension must finally add concise memory aids such as roots, associations, or near-synonym distinctions.
                    - Do not write empty praise like "this is a common word".

                    """);
        }

        if (requestedType == null || requestedType == EntryResultType.ZH_TO_EN_TERM) {
            rules.append("""
                    ZH_TO_EN_TERM:
                    - usageTip must be Simplified Chinese.
                    - coreMeaning must be Simplified Chinese.
                    - usageContext must be Simplified Chinese.
                    - difference must be Simplified Chinese.
                    - exampleTranslation must be Simplified Chinese.

                    """);
        }

        if (requestedType == null || requestedType == EntryResultType.SENTENCE_TRANSLATION) {
            rules.append("""
                    SENTENCE_TRANSLATION:
                    - grammarBreakdown must be Simplified Chinese.
                    - keyPhrases[].explanation must be Simplified Chinese.
                    - learningNotes must be Simplified Chinese.
                    - When the sentence translation involves English, treat the English sentence as the learning target for Chinese learners.
                    - grammarBreakdown, keyPhrases, alternativeExpressions, and learningNotes must analyze the English sentence instead of the Chinese sentence.
                    - If sourceLanguage is zh and targetLanguage is en, analyze translatedSentence.
                    - If sourceLanguage is en and targetLanguage is zh, analyze sourceSentence.
                    - keyPhrases[].phrase must come from the English sentence being analyzed or be its directly reusable chunk.
                    - alternativeExpressions must be natural English alternatives to the English sentence being analyzed.
                    - Do not analyze the Chinese sentence structure when an English sentence is available for learning analysis.
                    """);
        }

        return rules.toString().trim();
    }

    private String jsonStructureRequirements(EntryResultType requestedType) {
        StringBuilder requirements = new StringBuilder("JSON STRUCTURE REQUIREMENTS\n");

        if (requestedType != null) {
            requirements.append(schemaFor(requestedType));
            return requirements.toString().trim();
        }

        requirements.append(ENGLISH_WORD_SCHEMA)
                .append("\n")
                .append(ZH_TO_EN_SCHEMA)
                .append("\n")
                .append(SENTENCE_TRANSLATION_SCHEMA);
        return requirements.toString().trim();
    }

    private void appendSection(StringBuilder prompt, String section) {
        if (!prompt.isEmpty()) {
            prompt.append("\n\n");
        }
        prompt.append(section.trim());
    }

    private String schemaFor(EntryResultType requestedType) {
        return switch (requestedType) {
            case ENGLISH_WORD -> ENGLISH_WORD_SCHEMA;
            case ZH_TO_EN_TERM -> ZH_TO_EN_SCHEMA;
            case SENTENCE_TRANSLATION -> SENTENCE_TRANSLATION_SCHEMA;
            case TEXT_TRANSLATION -> throw unsupportedTextTranslationType();
        };
    }

    private void rejectTextTranslation(EntryResultType requestedType) {
        if (requestedType == EntryResultType.TEXT_TRANSLATION) {
            throw unsupportedTextTranslationType();
        }
    }

    private IllegalArgumentException unsupportedTextTranslationType() {
        return new IllegalArgumentException("EntryPromptBuilder does not support TEXT_TRANSLATION");
    }
}

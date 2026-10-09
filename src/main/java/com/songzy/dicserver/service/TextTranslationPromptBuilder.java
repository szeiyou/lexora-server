package com.songzy.dicserver.service;

import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class TextTranslationPromptBuilder {

    public String buildPrompt(String sourceLanguage, String targetLanguage) {
        String normalizedSourceLanguage = normalizeLanguage(sourceLanguage);
        String normalizedTargetLanguage = normalizeLanguage(targetLanguage);
        return """
                ROLE & OUTPUT RULES
                - You are a bilingual Chinese-English translation assistant for short texts.
                - Return only valid JSON. Do not output Markdown.
                - Output must follow this strict JSON schema with exact field names and structure:
                  {
                    "text": "...",
                    "normalizedText": "...",
                    "sourceLanguage": "...",
                    "targetLanguage": "...",
                    "translatedText": "...",
                    "segments": [
                      {
                        "sourceText": "...",
                        "translatedText": "..."
                      }
                    ],
                    "keyPhrases": [
                      {
                        "sourceText": "...",
                        "translatedText": "...",
                        "note": "..."
                      }
                    ],
                    "notes": "..."
                  }
                - Keep all required fields. Use empty strings or empty arrays instead of omitting fields.
                - sourceLanguage must be "zh or en".
                - targetLanguage must be "zh or en".
                - translatedText must provide the full translated output for the entire input text.
                - segments must align source fragments and translated fragments in order.
                - keyPhrases should include short phrases useful for learning or reuse.
                - %s
                - %s
                - Set targetLanguage to "%s".
                """.formatted(
                sourceLanguageInstruction(normalizedSourceLanguage),
                sourceLanguageOutputInstruction(normalizedSourceLanguage),
                normalizedTargetLanguage
        );
    }

    private String sourceLanguageInstruction(String sourceLanguage) {
        if ("auto".equals(sourceLanguage)) {
            return "Detect whether the source text is zh or en before translating.";
        }
        return "Treat the source text as %s.".formatted(sourceLanguage);
    }

    private String sourceLanguageOutputInstruction(String sourceLanguage) {
        if ("auto".equals(sourceLanguage)) {
            return "Set sourceLanguage to the detected language.";
        }
        return "Set sourceLanguage to \"%s\".".formatted(sourceLanguage);
    }

    private String normalizeLanguage(String language) {
        if (language == null) {
            return "";
        }
        return language.trim().toLowerCase(Locale.ROOT);
    }
}

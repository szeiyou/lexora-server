package com.songzy.dicserver.service;

import com.songzy.dicserver.model.TextTranslationResponse;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class TextTranslationService {

    private final TextTranslationCacheService textTranslationCacheService;
    private final TextTranslationPromptBuilder textTranslationPromptBuilder;
    private final LlmService llmService;
    private final TextTranslationResponseParser textTranslationResponseParser;
    private final TextTranslationKeyBuilder textTranslationKeyBuilder;
    private final QueryHistoryService queryHistoryService;
    private final QueryResultSnapshotService queryResultSnapshotService;

    public TextTranslationService(TextTranslationCacheService textTranslationCacheService,
                                  TextTranslationPromptBuilder textTranslationPromptBuilder,
                                  LlmService llmService,
                                  TextTranslationResponseParser textTranslationResponseParser,
                                  TextTranslationKeyBuilder textTranslationKeyBuilder,
                                  QueryHistoryService queryHistoryService,
                                  QueryResultSnapshotService queryResultSnapshotService) {
        this.textTranslationCacheService = textTranslationCacheService;
        this.textTranslationPromptBuilder = textTranslationPromptBuilder;
        this.llmService = llmService;
        this.textTranslationResponseParser = textTranslationResponseParser;
        this.textTranslationKeyBuilder = textTranslationKeyBuilder;
        this.queryHistoryService = queryHistoryService;
        this.queryResultSnapshotService = queryResultSnapshotService;
    }

    public TextTranslationResponse translate(Long userId,
                                             String rawText,
                                             String sourceLanguage,
                                             String targetLanguage) {
        String trimmedText = rawText == null ? null : rawText.trim();
        String normalizedText = textTranslationKeyBuilder.normalizeTextForStorage(trimmedText);
        String normalizedSourceLanguage = normalizeLanguage(sourceLanguage);
        String normalizedTargetLanguage = normalizeLanguage(targetLanguage);
        String cacheKey = textTranslationKeyBuilder.buildCacheKey(
                normalizedText,
                normalizedSourceLanguage,
                normalizedTargetLanguage
        );
        if (cacheKey == null) {
            throw new IllegalArgumentException("TextTranslationService requires text and translation languages");
        }

        return textTranslationCacheService.get(cacheKey)
                .or(() -> queryResultSnapshotService.findTextTranslationSnapshot(
                        normalizedText,
                        normalizedSourceLanguage,
                        normalizedTargetLanguage
                ))
                .orElseGet(() -> {
                    TextTranslationResponse response = fetchFromLlmAndPersist(
                            userId,
                            trimmedText,
                            normalizedText,
                            normalizedSourceLanguage,
                            normalizedTargetLanguage
                    );
                    queryResultSnapshotService.upsertTextTranslationSnapshot(
                            normalizedText,
                            normalizedSourceLanguage,
                            normalizedTargetLanguage,
                            response
                    );
                    textTranslationCacheService.put(cacheKey, response);
                    return response;
                });
    }

    private TextTranslationResponse fetchFromLlmAndPersist(Long userId,
                                                           String rawText,
                                                           String normalizedText,
                                                           String sourceLanguage,
                                                           String targetLanguage) {
        String prompt = textTranslationPromptBuilder.buildPrompt(sourceLanguage, targetLanguage);
        String rawJson = llmService.queryRawJson(rawText, prompt);
        TextTranslationResponse response = textTranslationResponseParser.parse(rawJson);
        reconcileRequestMetadata(response, rawText, normalizedText);
        queryHistoryService.recordTextTranslation(
                userId,
                rawText,
                normalizedText,
                sourceLanguage,
                targetLanguage,
                response
        );
        return response;
    }

    private void reconcileRequestMetadata(TextTranslationResponse response,
                                          String rawText,
                                          String normalizedText) {
        if (response == null) {
            return;
        }
        response.setText(rawText);
        response.setNormalizedText(normalizedText);
    }

    private String normalizeLanguage(String language) {
        if (language == null) {
            return null;
        }
        String normalized = language.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }
}

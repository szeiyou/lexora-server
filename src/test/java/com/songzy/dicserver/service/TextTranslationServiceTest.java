package com.songzy.dicserver.service;

import com.songzy.dicserver.model.TextTranslationResponse;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class TextTranslationServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private TextTranslationCacheService textTranslationCacheService;

    @Mock
    private TextTranslationPromptBuilder textTranslationPromptBuilder;

    @Mock
    private LlmService llmService;

    @Mock
    private TextTranslationResponseParser textTranslationResponseParser;

    @Mock
    private TextTranslationKeyBuilder textTranslationKeyBuilder;

    @Mock
    private QueryHistoryService queryHistoryService;

    @Mock
    private QueryResultSnapshotService queryResultSnapshotService;

    @InjectMocks
    private TextTranslationService textTranslationService;

    @BeforeEach
    void setUp() {
        lenient().when(textTranslationKeyBuilder.normalizeTextForStorage(anyString()))
                .thenAnswer(invocation -> {
                    String raw = invocation.getArgument(0);
                    if (raw == null) {
                        return null;
                    }
                    String normalized = raw.trim().replaceAll("\\s+", " ");
                    return normalized.isEmpty() ? null : normalized;
                });
        lenient().when(queryResultSnapshotService.findTextTranslationSnapshot(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
    }

    @Test
    void translateShouldReturnCachedResponseWhenPresent() {
        TextTranslationResponse cached = response("你好", "zh", "en", "hello");
        when(textTranslationKeyBuilder.buildCacheKey("你好", "zh", "en"))
                .thenReturn("TEXT_TRANSLATION:v1:abc");
        when(textTranslationCacheService.get("TEXT_TRANSLATION:v1:abc"))
                .thenReturn(Optional.of(cached));

        TextTranslationResponse result = textTranslationService.translate(USER_ID, "  你好  ", "zh", "en");

        assertThat(result).isSameAs(cached);
        verify(queryResultSnapshotService, never()).findTextTranslationSnapshot(anyString(), anyString(), anyString());
        verify(textTranslationPromptBuilder, never()).buildPrompt(anyString(), anyString());
        verify(llmService, never()).queryRawJson(anyString(), anyString());
    }

    @Test
    void translateShouldReuseSharedSnapshotBeforeCallingLlm() {
        TextTranslationResponse persisted = response("你好", "zh", "en", "hello");
        when(textTranslationKeyBuilder.buildCacheKey("你好", "zh", "en"))
                .thenReturn("TEXT_TRANSLATION:v1:abc");
        when(textTranslationCacheService.get("TEXT_TRANSLATION:v1:abc"))
                .thenReturn(Optional.empty());
        when(queryResultSnapshotService.findTextTranslationSnapshot("你好", "zh", "en"))
                .thenReturn(Optional.of(persisted));

        TextTranslationResponse result = textTranslationService.translate(USER_ID, "你好", "zh", "en");

        assertThat(result).isSameAs(persisted);
        verify(textTranslationPromptBuilder, never()).buildPrompt(anyString(), anyString());
        verify(llmService, never()).queryRawJson(anyString(), anyString());
        verify(queryResultSnapshotService, never()).upsertTextTranslationSnapshot(anyString(), anyString(), anyString(), any());
        verify(textTranslationCacheService, never()).put(anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void translateShouldPersistBeforeCachingFreshResult() {
        TextTranslationResponse response = response("你好", "zh", "en", "hello");
        when(textTranslationKeyBuilder.buildCacheKey("你好", "zh", "en"))
                .thenReturn("TEXT_TRANSLATION:v1:abc");
        when(textTranslationCacheService.get("TEXT_TRANSLATION:v1:abc"))
                .thenReturn(Optional.empty());
        when(queryResultSnapshotService.findTextTranslationSnapshot("你好", "zh", "en")).thenReturn(Optional.empty());
        when(textTranslationPromptBuilder.buildPrompt("zh", "en")).thenReturn("prompt");
        when(llmService.queryRawJson("你好", "prompt")).thenReturn("{\"translatedText\":\"hello\"}");
        when(textTranslationResponseParser.parse("{\"translatedText\":\"hello\"}")).thenReturn(response);

        TextTranslationResponse result = textTranslationService.translate(USER_ID, "你好", "zh", "en");

        assertThat(result).isSameAs(response);
        verify(textTranslationPromptBuilder).buildPrompt("zh", "en");
        InOrder inOrder = inOrder(queryHistoryService, queryResultSnapshotService, textTranslationCacheService);
        inOrder.verify(queryHistoryService).recordTextTranslation(USER_ID, "你好", "你好", "zh", "en", response);
        inOrder.verify(queryResultSnapshotService).upsertTextTranslationSnapshot("你好", "zh", "en", response);
        inOrder.verify(textTranslationCacheService).put("TEXT_TRANSLATION:v1:abc", response);
    }

    @Test
    void translateShouldPersistUsingRequestIdentityEvenWhenResponseReportsDetectedSourceLanguage() {
        TextTranslationResponse response = response("你好\n世界", "zh", "en", "hello world");
        when(textTranslationKeyBuilder.buildCacheKey("你好 世界", "auto", "en"))
                .thenReturn("TEXT_TRANSLATION:v1:auto-en");
        when(textTranslationCacheService.get("TEXT_TRANSLATION:v1:auto-en"))
                .thenReturn(Optional.empty());
        when(queryResultSnapshotService.findTextTranslationSnapshot("你好 世界", "auto", "en"))
                .thenReturn(Optional.empty());
        when(textTranslationPromptBuilder.buildPrompt("auto", "en")).thenReturn("prompt");
        when(llmService.queryRawJson("你好\n世界", "prompt")).thenReturn("{\"translatedText\":\"hello world\"}");
        when(textTranslationResponseParser.parse("{\"translatedText\":\"hello world\"}")).thenReturn(response);

        TextTranslationResponse result = textTranslationService.translate(USER_ID, "  你好\n世界  ", "auto", "en");

        assertThat(result).isSameAs(response);
        verify(textTranslationPromptBuilder).buildPrompt("auto", "en");
        verify(llmService).queryRawJson("你好\n世界", "prompt");
        verify(queryHistoryService).recordTextTranslation(USER_ID, "你好\n世界", "你好 世界", "auto", "en", response);
    }

    @Test
    void translateShouldReconcileRequestDerivedTextMetadataBeforePersistingCachingAndReturningFreshResult() {
        TextTranslationResponse response = response("model text", "zh", "en", "hello world");
        response.setNormalizedText("model-normalized");
        when(textTranslationKeyBuilder.buildCacheKey("你好 世界", "auto", "en"))
                .thenReturn("TEXT_TRANSLATION:v1:auto-en");
        when(textTranslationCacheService.get("TEXT_TRANSLATION:v1:auto-en"))
                .thenReturn(Optional.empty());
        when(queryResultSnapshotService.findTextTranslationSnapshot("你好 世界", "auto", "en"))
                .thenReturn(Optional.empty());
        when(textTranslationPromptBuilder.buildPrompt("auto", "en")).thenReturn("prompt");
        when(llmService.queryRawJson("你好\n世界", "prompt")).thenReturn("{\"translatedText\":\"hello world\"}");
        when(textTranslationResponseParser.parse("{\"translatedText\":\"hello world\"}")).thenReturn(response);

        TextTranslationResponse result = textTranslationService.translate(USER_ID, "  你好\n世界  ", "auto", "en");

        assertThat(result).isSameAs(response);
        assertThat(result.getText()).isEqualTo("你好\n世界");
        assertThat(result.getNormalizedText()).isEqualTo("你好 世界");

        ArgumentCaptor<TextTranslationResponse> historyCaptor = ArgumentCaptor.forClass(TextTranslationResponse.class);
        ArgumentCaptor<TextTranslationResponse> snapshotCaptor = ArgumentCaptor.forClass(TextTranslationResponse.class);
        ArgumentCaptor<TextTranslationResponse> cacheCaptor = ArgumentCaptor.forClass(TextTranslationResponse.class);
        InOrder inOrder = inOrder(queryHistoryService, queryResultSnapshotService, textTranslationCacheService);
        inOrder.verify(queryHistoryService)
                .recordTextTranslation(
                        eq(USER_ID),
                        eq("你好\n世界"),
                        eq("你好 世界"),
                        eq("auto"),
                        eq("en"),
                        historyCaptor.capture()
                );
        inOrder.verify(queryResultSnapshotService)
                .upsertTextTranslationSnapshot(eq("你好 世界"), eq("auto"), eq("en"), snapshotCaptor.capture());
        inOrder.verify(textTranslationCacheService)
                .put(eq("TEXT_TRANSLATION:v1:auto-en"), cacheCaptor.capture());
        assertThat(historyCaptor.getValue().getText()).isEqualTo("你好\n世界");
        assertThat(historyCaptor.getValue().getNormalizedText()).isEqualTo("你好 世界");
        assertThat(snapshotCaptor.getValue().getText()).isEqualTo("你好\n世界");
        assertThat(snapshotCaptor.getValue().getNormalizedText()).isEqualTo("你好 世界");
        assertThat(cacheCaptor.getValue().getText()).isEqualTo("你好\n世界");
        assertThat(cacheCaptor.getValue().getNormalizedText()).isEqualTo("你好 世界");
    }

    @Test
    void translateShouldNotMutateCacheWhenPersistenceFails() {
        TextTranslationResponse response = response("你好", "zh", "en", "hello");
        when(textTranslationKeyBuilder.buildCacheKey("你好", "zh", "en"))
                .thenReturn("TEXT_TRANSLATION:v1:abc");
        when(textTranslationCacheService.get("TEXT_TRANSLATION:v1:abc"))
                .thenReturn(Optional.empty());
        when(queryResultSnapshotService.findTextTranslationSnapshot("你好", "zh", "en")).thenReturn(Optional.empty());
        when(textTranslationPromptBuilder.buildPrompt("zh", "en")).thenReturn("prompt");
        when(llmService.queryRawJson("你好", "prompt")).thenReturn("{\"translatedText\":\"hello\"}");
        when(textTranslationResponseParser.parse("{\"translatedText\":\"hello\"}")).thenReturn(response);
        org.mockito.Mockito.doThrow(new IllegalStateException("persist failed"))
                .when(queryHistoryService)
                .recordTextTranslation(USER_ID, "你好", "你好", "zh", "en", response);

        assertThatThrownBy(() -> textTranslationService.translate(USER_ID, "你好", "zh", "en"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("persist failed");
        verify(queryResultSnapshotService, never()).upsertTextTranslationSnapshot(anyString(), anyString(), anyString(), any());
        verify(textTranslationCacheService, never()).put("TEXT_TRANSLATION:v1:abc", response);
    }

    private TextTranslationResponse response(String text,
                                             String sourceLanguage,
                                             String targetLanguage,
                                             String translatedText) {
        TextTranslationResponse response = new TextTranslationResponse();
        response.setText(text);
        response.setNormalizedText(text);
        response.setSourceLanguage(sourceLanguage);
        response.setTargetLanguage(targetLanguage);
        response.setTranslatedText(translatedText);
        response.setSegments(java.util.List.of());
        response.setKeyPhrases(java.util.List.of());
        response.setNotes("");
        return response;
    }
}

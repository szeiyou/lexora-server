package com.songzy.dicserver.controller;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.songzy.dicserver.exception.BusinessException;
import com.songzy.dicserver.exception.GlobalExceptionHandler;
import com.songzy.dicserver.dto.QueryHistoryDetailDTO;
import com.songzy.dicserver.dto.QueryHistorySummaryDTO;
import com.songzy.dicserver.model.EntryQueryResponse;
import com.songzy.dicserver.model.EntryResultType;
import com.songzy.dicserver.model.TextTranslationResponse;
import com.songzy.dicserver.security.CurrentUserPrincipal;
import com.songzy.dicserver.service.AudioGenerationTaskService;
import com.songzy.dicserver.service.EntryAudioService;
import com.songzy.dicserver.service.EntryQueryService;
import com.songzy.dicserver.service.QueryHistoryService;
import com.songzy.dicserver.service.TextTranslationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DictionaryControllerTest {

    private static final Long USER_ID = 7L;

    @Mock
    private EntryQueryService entryQueryService;

    @Mock
    private EntryAudioService entryAudioService;

    @Mock
    private AudioGenerationTaskService audioGenerationTaskService;

    @Mock
    private QueryHistoryService queryHistoryService;

    @Mock
    private TextTranslationService textTranslationService;

    @InjectMocks
    private DictionaryController dictionaryController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(dictionaryController)
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver(),
                        new PageableHandlerMethodArgumentResolver()
                )
                .setMessageConverters(
                        new ByteArrayHttpMessageConverter(),
                        new MappingJackson2HttpMessageConverter(
                                Jackson2ObjectMapperBuilder.json()
                                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                                        .build()
                        )
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .addDispatcherServletCustomizer(dispatcherServlet -> dispatcherServlet.setThrowExceptionIfNoHandlerFound(false))
                .build();
    }

    @Test
    void getWordInfoShouldReturn404AfterLegacyRouteRemoval() throws Exception {
        mockMvc.perform(get("/api/v1/words/test"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getEntryShouldReturnMissingParameterResponseWhenQMissing() throws Exception {
        mockMvc.perform(get("/api/v1/entries").param("type", "ENGLISH_WORD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"));
    }

    @Test
    void getEntryShouldReturnValidationErrorWhenQIsBlank() throws Exception {
        mockMvc.perform(get("/api/v1/entries").param("q", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void getEntryShouldReturnValidationErrorWhenQIsTooLong() throws Exception {
        String oversizedQuery = "a".repeat(301);

        mockMvc.perform(get("/api/v1/entries").param("q", oversizedQuery))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void getEntryShouldAcceptQueryWhenOnlyRawWhitespacePushesLengthPastLimit() throws Exception {
        String paddedQuery = " ".repeat(10) + "a".repeat(300) + " ".repeat(10);

        runAsUser(() -> mockMvc.perform(get("/api/v1/entries").param("q", paddedQuery).param("type", "ENGLISH_WORD"))
                .andExpect(status().isOk()));

        verify(entryQueryService).query(
                eq(USER_ID),
                eq("a".repeat(300)),
                eq(com.songzy.dicserver.model.EntryResultType.ENGLISH_WORD)
        );
    }

    @Test
    void getEntryShouldReturn400WhenTypeIsInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/entries").param("q", "hello").param("type", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER_TYPE"));
    }

    @Test
    void getEntryShouldRejectTextTranslationTypeAtControllerBoundary() throws Exception {
        mockMvc.perform(get("/api/v1/entries").param("q", "hello").param("type", "TEXT_TRANSLATION"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(entryQueryService, never()).query(any(), any(), any());
    }

    @Test
    void refreshEntryShouldReturnMissingParameterResponseWhenQMissing() throws Exception {
        mockMvc.perform(post("/api/v1/entries/refresh").param("type", "ENGLISH_WORD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"));
    }

    @Test
    void refreshEntryShouldReturnValidationErrorWhenQIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/entries/refresh").param("q", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void refreshEntryShouldTrimQueryAndDelegateToEntryQueryService() throws Exception {
        runAsUser(() -> mockMvc.perform(post("/api/v1/entries/refresh")
                        .param("q", "  visible  ")
                        .param("type", "ENGLISH_WORD"))
                .andExpect(status().isOk()));

        verify(entryQueryService).refresh(USER_ID, "visible", com.songzy.dicserver.model.EntryResultType.ENGLISH_WORD);
    }

    @Test
    void refreshEntryShouldRejectTextTranslationTypeAtControllerBoundary() throws Exception {
        mockMvc.perform(post("/api/v1/entries/refresh")
                        .param("q", "hello")
                        .param("type", "TEXT_TRANSLATION"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(entryQueryService, never()).refresh(any(), any(), any());
    }

    @Test
    void translateTextShouldReturn200AndDelegateToTextTranslationService() throws Exception {
        TextTranslationResponse response = textTranslationResponse("你好", "zh", "en", "hello");
        when(textTranslationService.translate(USER_ID, "你好", "auto", "en")).thenReturn(response);

        runAsUser(() -> mockMvc.perform(post("/api/v1/text-translations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "  你好  ",
                                  "targetLanguage": "en"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.translatedText").value("hello"))
                .andExpect(jsonPath("$.sourceLanguage").value("zh"))
                .andExpect(jsonPath("$.targetLanguage").value("en")));

        verify(textTranslationService).translate(USER_ID, "你好", "auto", "en");
    }

    @Test
    void translateTextShouldRejectMissingText() throws Exception {
        mockMvc.perform(post("/api/v1/text-translations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetLanguage": "en"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void translateTextShouldRejectBlankText() throws Exception {
        mockMvc.perform(post("/api/v1/text-translations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "   ",
                                  "targetLanguage": "en"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void translateTextShouldRejectTrimmedTextLongerThan3000Characters() throws Exception {
        mockMvc.perform(post("/api/v1/text-translations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "%s",
                                  "targetLanguage": "en"
                                }
                                """.formatted("a".repeat(3001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void translateTextShouldAcceptWhenOnlySurroundingWhitespacePushesRawLengthPastLimit() throws Exception {
        String paddedText = " ".repeat(20) + "a".repeat(3000) + " ".repeat(20);
        TextTranslationResponse response = textTranslationResponse("a".repeat(3000), "en", "zh", "好");
        when(textTranslationService.translate(USER_ID, "a".repeat(3000), "auto", "zh")).thenReturn(response);

        runAsUser(() -> mockMvc.perform(post("/api/v1/text-translations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "%s",
                                  "targetLanguage": "zh"
                                }
                                """.formatted(paddedText)))
                .andExpect(status().isOk()));

        verify(textTranslationService).translate(USER_ID, "a".repeat(3000), "auto", "zh");
    }

    @Test
    void translateTextShouldRejectMissingTargetLanguage() throws Exception {
        mockMvc.perform(post("/api/v1/text-translations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "你好"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void translateTextShouldRejectInvalidLanguageCode() throws Exception {
        mockMvc.perform(post("/api/v1/text-translations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "你好",
                                  "sourceLanguage": "fr",
                                  "targetLanguage": "en"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void translateTextShouldReturnDeterministic400ForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/text-translations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"你好\","))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void getHistoryShouldReturnPagedSummaryDtosFromQueryHistoryService() throws Exception {
        QueryHistorySummaryDTO summary = new QueryHistorySummaryDTO(
                "fp-visible",
                "visible",
                "visible",
                EntryResultType.ENGLISH_WORD,
                "可见的",
                LocalDateTime.of(2026, 3, 21, 12, 34, 56),
                2,
                List.of(
                        LocalDateTime.of(2026, 3, 21, 12, 34, 56),
                        LocalDateTime.of(2026, 3, 20, 8, 0, 0)
                )
        );
        when(queryHistoryService.getHistory(eq(USER_ID), any())).thenReturn(
                new PageImpl<>(List.of(summary), PageRequest.of(0, 20), 1)
        );

        runAsUser(() -> mockMvc.perform(get("/api/v1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].historyKey").value("fp-visible"))
                .andExpect(jsonPath("$.content[0].query").value("visible"))
                .andExpect(jsonPath("$.content[0].normalizedQuery").value("visible"))
                .andExpect(jsonPath("$.content[0].resultType").value("ENGLISH_WORD"))
                .andExpect(jsonPath("$.content[0].summary").value("可见的"))
                .andExpect(jsonPath("$.content[0].sourceApi").doesNotExist())
                .andExpect(jsonPath("$.content[0].latestSearchTime").value("2026-03-21T12:34:56"))
                .andExpect(jsonPath("$.content[0].searchCount").value(2))
                .andExpect(jsonPath("$.content[0].searchTimes[0]").value("2026-03-21T12:34:56"))
                .andExpect(jsonPath("$.content[0].searchTimes[1]").value("2026-03-20T08:00:00"))
                .andExpect(jsonPath("$.content[0].id").doesNotExist())
                .andExpect(jsonPath("$.content[0].searchTime").doesNotExist()));
    }

    @Test
    void getHistoryDetailShouldReturnTextTranslationDetailWhenHistoryKeyExists() throws Exception {
        QueryHistoryDetailDTO detail = new QueryHistoryDetailDTO(
                "fp-text",
                EntryResultType.TEXT_TRANSLATION,
                "你好",
                "你好",
                LocalDateTime.of(2026, 3, 21, 12, 34, 56),
                2,
                List.of(
                        LocalDateTime.of(2026, 3, 21, 12, 34, 56),
                        LocalDateTime.of(2026, 3, 20, 8, 0, 0)
                ),
                textTranslationResponse("你好", "zh", "en", "hello")
        );
        when(queryHistoryService.getHistoryDetail(USER_ID, "fp-text")).thenReturn(Optional.of(detail));

        runAsUser(() -> mockMvc.perform(get("/api/v1/history/fp-text"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historyKey").value("fp-text"))
                .andExpect(jsonPath("$.resultType").value("TEXT_TRANSLATION"))
                .andExpect(jsonPath("$.query").value("你好"))
                .andExpect(jsonPath("$.normalizedQuery").value("你好"))
                .andExpect(jsonPath("$.latestSearchTime").value("2026-03-21T12:34:56"))
                .andExpect(jsonPath("$.searchCount").value(2))
                .andExpect(jsonPath("$.searchTimes[0]").value("2026-03-21T12:34:56"))
                .andExpect(jsonPath("$.searchTimes[1]").value("2026-03-20T08:00:00"))
                .andExpect(jsonPath("$.response.translatedText").value("hello")));
    }

    @Test
    void getHistoryDetailShouldReturnEntryQueryDetailWhenHistoryKeyExists() throws Exception {
        EntryQueryResponse entryResponse = englishWordResponse("visible");
        QueryHistoryDetailDTO detail = new QueryHistoryDetailDTO(
                "fp-entry",
                EntryResultType.ENGLISH_WORD,
                "visible",
                "visible",
                LocalDateTime.of(2026, 3, 21, 9, 10, 11),
                1,
                List.of(LocalDateTime.of(2026, 3, 21, 9, 10, 11)),
                entryResponse
        );
        when(queryHistoryService.getHistoryDetail(USER_ID, "fp-entry")).thenReturn(Optional.of(detail));

        runAsUser(() -> mockMvc.perform(get("/api/v1/history/fp-entry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historyKey").value("fp-entry"))
                .andExpect(jsonPath("$.resultType").value("ENGLISH_WORD"))
                .andExpect(jsonPath("$.query").value("visible"))
                .andExpect(jsonPath("$.normalizedQuery").value("visible"))
                .andExpect(jsonPath("$.latestSearchTime").value("2026-03-21T09:10:11"))
                .andExpect(jsonPath("$.searchCount").value(1))
                .andExpect(jsonPath("$.searchTimes[0]").value("2026-03-21T09:10:11"))
                .andExpect(jsonPath("$.response.englishWord.word").value("visible")));
    }

    @Test
    void getRecentSearchesShouldReturn404AfterRouteRemoval() throws Exception {
        mockMvc.perform(get("/api/v1/recent-searches"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getHistoryDetailShouldReturn404WhenHistoryKeyMissing() throws Exception {
        when(queryHistoryService.getHistoryDetail(USER_ID, "missing")).thenReturn(Optional.empty());

        runAsUser(() -> mockMvc.perform(get("/api/v1/history/missing"))
                .andExpect(status().isNotFound()));

        verify(queryHistoryService).getHistoryDetail(USER_ID, "missing");
    }

    @Test
    void getAudioByKeyShouldCanonicalizeColonContainingIncomingKeysBeforeLookup() throws Exception {
        when(entryAudioService.getAudioByKey("english_word:headword:rock and roll")).thenReturn(null);
        when(audioGenerationTaskService.isAwaitingAudio("english_word:headword:rock and roll")).thenReturn(false);

        mockMvc.perform(get("/api/v1/audio/by-key/{audioKey}", "english_word:headword:rock:and:roll"))
                .andExpect(status().isNotFound());

        verify(entryAudioService).getAudioByKey(eq("english_word:headword:rock and roll"));
        verify(audioGenerationTaskService).isAwaitingAudio(eq("english_word:headword:rock and roll"));
        verify(entryAudioService, never()).getAudioByKey(eq("english_word:headword:rock:and:roll"));
    }

    @Test
    void getWordAudioShouldReturn404AfterLegacyRouteRemoval() throws Exception {
        mockMvc.perform(get("/api/v1/audio/hello"))
                .andExpect(status().isNotFound());
    }

    @Test
    void refreshWordInfoShouldReturn404AfterLegacyRouteRemoval() throws Exception {
        mockMvc.perform(post("/api/v1/words/hello/refresh"))
                .andExpect(status().isNotFound());
    }

    private TextTranslationResponse textTranslationResponse(String text,
                                                           String sourceLanguage,
                                                           String targetLanguage,
                                                           String translatedText) {
        TextTranslationResponse response = new TextTranslationResponse();
        response.setText(text);
        response.setNormalizedText(text);
        response.setSourceLanguage(sourceLanguage);
        response.setTargetLanguage(targetLanguage);
        response.setTranslatedText(translatedText);
        response.setSegments(List.of());
        response.setKeyPhrases(List.of());
        response.setNotes("");
        return response;
    }

    private EntryQueryResponse englishWordResponse(String word) {
        EntryQueryResponse response = new EntryQueryResponse();
        response.setQuery(word);
        response.setNormalizedQuery(word);
        response.setResultType(EntryResultType.ENGLISH_WORD);
        response.setSourceLanguage("en");
        response.setTargetLanguage("zh");
        EntryQueryResponse.EnglishWordPayload payload = new EntryQueryResponse.EnglishWordPayload();
        payload.setWord(word);
        response.setEnglishWord(payload);
        return response;
    }

    private void runAsUser(ThrowingRunnable action) throws Exception {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentUserPrincipal(USER_ID, "alice"),
                "access-token",
                List.of()
        ));
        SecurityContextHolder.setContext(context);
        try {
            action.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}

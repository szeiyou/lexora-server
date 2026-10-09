package com.songzy.dicserver.controller;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.songzy.dicserver.dto.QueryHistoryDetailDTO;
import com.songzy.dicserver.dto.QueryHistorySummaryDTO;
import com.songzy.dicserver.exception.GlobalExceptionHandler;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DictionaryControllerIntegrationTest {

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
        mockMvc.perform(get("/api/v1/words/hello"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getEntryShouldReturn200WhenEnglishWordTypeIsRequested() throws Exception {
        EntryQueryResponse response = createEntryResponse(EntryResultType.ENGLISH_WORD);
        when(entryQueryService.query(USER_ID, "hello", EntryResultType.ENGLISH_WORD)).thenReturn(response);

        runAsUser(() -> mockMvc.perform(get("/api/v1/entries").param("q", "hello").param("type", "ENGLISH_WORD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultType").value("ENGLISH_WORD")));
    }

    @Test
    void getEntryShouldReturn200WhenZhToEnTypeIsRequested() throws Exception {
        EntryQueryResponse response = createEntryResponse(EntryResultType.ZH_TO_EN_TERM);
        when(entryQueryService.query(USER_ID, "你好", EntryResultType.ZH_TO_EN_TERM)).thenReturn(response);

        runAsUser(() -> mockMvc.perform(get("/api/v1/entries").param("q", "你好").param("type", "ZH_TO_EN_TERM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultType").value("ZH_TO_EN_TERM")));
    }

    @Test
    void getEntryShouldTreatBlankTypeAsAutoClassification() throws Exception {
        EntryQueryResponse response = createEntryResponse(EntryResultType.ZH_TO_EN_TERM);
        when(entryQueryService.query(USER_ID, "苹果", null)).thenReturn(response);

        runAsUser(() -> mockMvc.perform(get("/api/v1/entries").param("q", "苹果").param("type", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultType").value("ZH_TO_EN_TERM")));

        verify(entryQueryService).query(eq(USER_ID), eq("苹果"), isNull());
    }

    @Test
    void refreshEntryShouldReturn200WhenEnglishWordTypeIsRequested() throws Exception {
        EntryQueryResponse response = createEntryResponse(EntryResultType.ENGLISH_WORD);
        when(entryQueryService.refresh(USER_ID, "visible", EntryResultType.ENGLISH_WORD)).thenReturn(response);

        runAsUser(() -> mockMvc.perform(post("/api/v1/entries/refresh")
                        .param("q", "visible")
                        .param("type", "ENGLISH_WORD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultType").value("ENGLISH_WORD")));
    }

    @Test
    void translateTextShouldReturnSerializedResponse() throws Exception {
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
                .andExpect(jsonPath("$.text").value("你好"))
                .andExpect(jsonPath("$.translatedText").value("hello"))
                .andExpect(jsonPath("$.sourceLanguage").value("zh"))
                .andExpect(jsonPath("$.targetLanguage").value("en")));
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
    void getHistoryShouldReturnPagedSummaryDtos() throws Exception {
        QueryHistorySummaryDTO dto = new QueryHistorySummaryDTO(
                "fp-visible",
                "visible",
                "visible",
                EntryResultType.ENGLISH_WORD,
                "可见的",
                LocalDateTime.of(2026, 3, 21, 11, 22, 33),
                2,
                List.of(
                        LocalDateTime.of(2026, 3, 21, 11, 22, 33),
                        LocalDateTime.of(2026, 3, 20, 9, 0, 0)
                )
        );
        when(queryHistoryService.getHistory(eq(USER_ID), org.mockito.ArgumentMatchers.any())).thenReturn(
                new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1)
        );

        runAsUser(() -> mockMvc.perform(get("/api/v1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].historyKey").value("fp-visible"))
                .andExpect(jsonPath("$.content[0].query").value("visible"))
                .andExpect(jsonPath("$.content[0].normalizedQuery").value("visible"))
                .andExpect(jsonPath("$.content[0].resultType").value("ENGLISH_WORD"))
                .andExpect(jsonPath("$.content[0].summary").value("可见的"))
                .andExpect(jsonPath("$.content[0].sourceApi").doesNotExist())
                .andExpect(jsonPath("$.content[0].latestSearchTime").value("2026-03-21T11:22:33"))
                .andExpect(jsonPath("$.content[0].searchCount").value(2))
                .andExpect(jsonPath("$.content[0].searchTimes[0]").value("2026-03-21T11:22:33"))
                .andExpect(jsonPath("$.content[0].searchTimes[1]").value("2026-03-20T09:00:00"))
                .andExpect(jsonPath("$.content[0].id").doesNotExist())
                .andExpect(jsonPath("$.content[0].searchTime").doesNotExist())
                .andExpect(jsonPath("$.content[0].responseJson").doesNotExist())
                .andExpect(jsonPath("$.content[0].responseSchema").doesNotExist()));
    }

    @Test
    void getHistoryDetailShouldReturnEntryQueryDetail() throws Exception {
        EntryQueryResponse response = createEntryResponse(EntryResultType.ENGLISH_WORD);
        QueryHistoryDetailDTO dto = new QueryHistoryDetailDTO(
                "fp-entry",
                EntryResultType.ENGLISH_WORD,
                "hello",
                "hello",
                LocalDateTime.of(2026, 3, 21, 11, 22, 33),
                2,
                List.of(
                        LocalDateTime.of(2026, 3, 21, 11, 22, 33),
                        LocalDateTime.of(2026, 3, 20, 9, 0, 0)
                ),
                response
        );
        when(queryHistoryService.getHistoryDetail(USER_ID, "fp-entry")).thenReturn(Optional.of(dto));

        runAsUser(() -> mockMvc.perform(get("/api/v1/history/fp-entry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historyKey").value("fp-entry"))
                .andExpect(jsonPath("$.resultType").value("ENGLISH_WORD"))
                .andExpect(jsonPath("$.query").value("hello"))
                .andExpect(jsonPath("$.normalizedQuery").value("hello"))
                .andExpect(jsonPath("$.latestSearchTime").value("2026-03-21T11:22:33"))
                .andExpect(jsonPath("$.searchCount").value(2))
                .andExpect(jsonPath("$.searchTimes[0]").value("2026-03-21T11:22:33"))
                .andExpect(jsonPath("$.searchTimes[1]").value("2026-03-20T09:00:00"))
                .andExpect(jsonPath("$.response.englishWord.word").value("hello")));
    }

    @Test
    void getHistoryDetailShouldReturnTextTranslationDetail() throws Exception {
        TextTranslationResponse response = textTranslationResponse("你好", "zh", "en", "hello");
        QueryHistoryDetailDTO dto = new QueryHistoryDetailDTO(
                "fp-text",
                EntryResultType.TEXT_TRANSLATION,
                "你好",
                "你好",
                LocalDateTime.of(2026, 3, 21, 11, 22, 33),
                1,
                List.of(LocalDateTime.of(2026, 3, 21, 11, 22, 33)),
                response
        );
        when(queryHistoryService.getHistoryDetail(USER_ID, "fp-text")).thenReturn(Optional.of(dto));

        runAsUser(() -> mockMvc.perform(get("/api/v1/history/fp-text"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historyKey").value("fp-text"))
                .andExpect(jsonPath("$.resultType").value("TEXT_TRANSLATION"))
                .andExpect(jsonPath("$.query").value("你好"))
                .andExpect(jsonPath("$.normalizedQuery").value("你好"))
                .andExpect(jsonPath("$.latestSearchTime").value("2026-03-21T11:22:33"))
                .andExpect(jsonPath("$.searchCount").value(1))
                .andExpect(jsonPath("$.searchTimes[0]").value("2026-03-21T11:22:33"))
                .andExpect(jsonPath("$.response.translatedText").value("hello")));
    }

    @Test
    void getHistoryDetailShouldReturn404WhenHistoryKeyMissing() throws Exception {
        when(queryHistoryService.getHistoryDetail(USER_ID, "missing")).thenReturn(Optional.empty());

        runAsUser(() -> mockMvc.perform(get("/api/v1/history/missing"))
                .andExpect(status().isNotFound()));

        verify(queryHistoryService).getHistoryDetail(USER_ID, "missing");
    }

    @Test
    void getRecentSearchesShouldReturn404AfterRouteRemoval() throws Exception {
        mockMvc.perform(get("/api/v1/recent-searches"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getWordAudioShouldReturn404AfterLegacyRouteRemoval() throws Exception {
        mockMvc.perform(get("/api/v1/audio/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void refreshWordInfoShouldReturn404AfterLegacyRouteRemoval() throws Exception {
        mockMvc.perform(post("/api/v1/words/hello/refresh"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAudioByKeyShouldReturn404WhenNotFound() throws Exception {
        when(entryAudioService.getAudioByKey("english_word:missing")).thenReturn(null);
        when(audioGenerationTaskService.isAwaitingAudio("english_word:missing")).thenReturn(false);

        mockMvc.perform(get("/api/v1/audio/by-key/english_word:missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAudioByKeyShouldReturn202WhenPendingTaskExistsButAudioMissing() throws Exception {
        when(entryAudioService.getAudioByKey("english_word:pending")).thenReturn(null);
        when(audioGenerationTaskService.isAwaitingAudio("english_word:pending")).thenReturn(true);

        mockMvc.perform(get("/api/v1/audio/by-key/english_word:pending"))
                .andExpect(status().isAccepted());
    }

    @Test
    void getAudioByKeyShouldReturn202WhenProcessingTaskExistsButAudioMissing() throws Exception {
        when(entryAudioService.getAudioByKey("english_word:processing")).thenReturn(null);
        when(audioGenerationTaskService.isAwaitingAudio("english_word:processing")).thenReturn(true);

        mockMvc.perform(get("/api/v1/audio/by-key/english_word:processing"))
                .andExpect(status().isAccepted());
    }

    @Test
    void getAudioByKeyShouldReturn202WhenRetryableFailedTaskExistsButAudioMissing() throws Exception {
        when(entryAudioService.getAudioByKey("english_word:failed-retryable")).thenReturn(null);
        when(audioGenerationTaskService.isAwaitingAudio("english_word:failed-retryable")).thenReturn(true);

        mockMvc.perform(get("/api/v1/audio/by-key/english_word:failed-retryable"))
                .andExpect(status().isAccepted());
    }

    @Test
    void getAudioByKeyShouldReturn404WhenTaskIsTerminalFailed() throws Exception {
        when(entryAudioService.getAudioByKey("english_word:failed-terminal")).thenReturn(null);
        when(audioGenerationTaskService.isAwaitingAudio("english_word:failed-terminal")).thenReturn(false);

        mockMvc.perform(get("/api/v1/audio/by-key/english_word:failed-terminal"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAudioByKeyShouldReturn200WithData() throws Exception {
        when(entryAudioService.getAudioByKey("ice cream")).thenReturn(new byte[]{4, 5, 6});

        mockMvc.perform(get("/api/v1/audio/by-key/{audioKey}", "ice cream"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.parseMediaType("audio/mpeg")));
    }

    @Test
    void getAudioByKeyShouldCanonicalizeEquivalentUnicodeAudioKeyBeforeLookup() throws Exception {
        when(entryAudioService.getAudioByKey("english_word:headword:é")).thenReturn(new byte[]{7, 8, 9});

        mockMvc.perform(get("/api/v1/audio/by-key/{audioKey}", "english_word:headword:e\u0301"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.parseMediaType("audio/mpeg")));
    }

    @Test
    void getAudioByKeyShouldTreatColonContainingIncomingKeysAsEquivalentToCanonicalKey() throws Exception {
        when(entryAudioService.getAudioByKey("english_word:headword:rock and roll")).thenReturn(new byte[]{7, 8, 9});

        mockMvc.perform(get("/api/v1/audio/by-key/{audioKey}", "english_word:headword:rock:and:roll"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.parseMediaType("audio/mpeg")));
    }

    private EntryQueryResponse createEntryResponse(EntryResultType resultType) {
        EntryQueryResponse response = new EntryQueryResponse();
        response.setQuery(resultType == EntryResultType.ENGLISH_WORD ? "hello" : "你好");
        response.setNormalizedQuery(response.getQuery());
        response.setResultType(resultType);
        response.setSourceLanguage(resultType == EntryResultType.ENGLISH_WORD ? "en" : "zh");
        response.setTargetLanguage(resultType == EntryResultType.ENGLISH_WORD ? "zh" : "en");

        if (resultType == EntryResultType.ENGLISH_WORD) {
            EntryQueryResponse.EnglishWordPayload payload = new EntryQueryResponse.EnglishWordPayload();
            payload.setWord("hello");
            response.setEnglishWord(payload);
        } else if (resultType == EntryResultType.ZH_TO_EN_TERM) {
            EntryQueryResponse.ZhToEnTermPayload payload = new EntryQueryResponse.ZhToEnTermPayload();
            payload.setInput("你好");
            payload.setCandidates(List.of());
            response.setZhToEnTerm(payload);
        }
        return response;
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

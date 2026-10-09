package com.songzy.dicserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songzy.dicserver.model.AppUser;
import com.songzy.dicserver.model.EntryQueryResponse;
import com.songzy.dicserver.model.EntryResultType;
import com.songzy.dicserver.model.UserStatus;
import com.songzy.dicserver.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest
@ActiveProfiles("test")
@Import({
        QueryHistoryService.class,
        QueryHistorySummaryBuilder.class,
        QueryResultSnapshotService.class,
        QueryTextNormalizer.class,
        TextTranslationKeyBuilder.class,
        EntryCacheService.class,
        EntryQueryService.class,
        EntryQueryServicePersistentFallbackTest.TestBeans.class
})
class EntryQueryServicePersistentFallbackTest {

    private Long userId;

    @TestConfiguration
    static class TestBeans {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    private EntryQueryService entryQueryService;

    @Autowired
    private QueryResultSnapshotService queryResultSnapshotService;

    @Autowired
    private AppUserRepository appUserRepository;

    @MockitoBean
    private EntryPromptBuilder entryPromptBuilder;

    @MockitoBean
    private LlmService llmService;

    @MockitoBean
    private EntryResponseParser entryResponseParser;

    @MockitoBean
    private EntryAudioService entryAudioService;

    @MockitoBean
    private EnglishWordSnapshotService englishWordSnapshotService;

    @BeforeEach
    void setUp() {
        AppUser user = new AppUser();
        user.setUsername("alice");
        user.setPasswordHash("hashed-password");
        user.setStatus(UserStatus.ACTIVE);
        userId = appUserRepository.saveAndFlush(user).getId();
        when(entryAudioService.enrichHeadwordAudio(any(EntryQueryResponse.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void queryShouldReuseSharedSnapshotWhenMemoryCacheMisses() {
        EntryQueryResponse persisted = englishWordResponse("visible", "持久化释义");
        queryResultSnapshotService.upsertEntrySnapshot("visible", persisted);

        when(entryPromptBuilder.buildPrompt(EntryResultType.ENGLISH_WORD)).thenReturn("prompt");
        when(llmService.queryRawJson("visible", "prompt")).thenReturn("{\"resultType\":\"ENGLISH_WORD\"}");
        when(entryResponseParser.parse("{\"resultType\":\"ENGLISH_WORD\"}", EntryResultType.ENGLISH_WORD))
                .thenReturn(englishWordResponse("visible", "新的释义"));

        EntryQueryResponse result = entryQueryService.query(userId, "visible", EntryResultType.ENGLISH_WORD);

        assertThat(result.getEnglishWord().getDefinitions().getFirst().getMeaning()).isEqualTo("持久化释义");
        verify(llmService, never()).queryRawJson(any(), any());
    }

    @Test
    void queryShouldReuseLatestSharedSnapshotForAutoDetectedType() {
        EntryQueryResponse persisted = zhToEnResponse("苹果", "apple", "持久化候选");
        queryResultSnapshotService.upsertEntrySnapshot("苹果", persisted);

        when(entryPromptBuilder.buildPrompt(null)).thenReturn("prompt");
        when(llmService.queryRawJson("苹果", "prompt")).thenReturn("{\"resultType\":\"ZH_TO_EN_TERM\"}");
        when(entryResponseParser.parse("{\"resultType\":\"ZH_TO_EN_TERM\"}", null))
                .thenReturn(zhToEnResponse("苹果", "apple", "新的候选"));

        EntryQueryResponse result = entryQueryService.query(userId, "苹果", null);

        assertThat(result.getResultType()).isEqualTo(EntryResultType.ZH_TO_EN_TERM);
        assertThat(result.getZhToEnTerm().getCandidates().getFirst().getCoreMeaning()).isEqualTo("持久化候选");
        verify(llmService, never()).queryRawJson(any(), any());
    }

    private EntryQueryResponse englishWordResponse(String word, String meaning) {
        EntryQueryResponse response = new EntryQueryResponse();
        response.setQuery(word);
        response.setNormalizedQuery(word.toLowerCase());
        response.setResultType(EntryResultType.ENGLISH_WORD);
        response.setSourceLanguage("en");
        response.setTargetLanguage("zh");

        EntryQueryResponse.Definition definition = new EntryQueryResponse.Definition();
        definition.setPartOfSpeech("adj.");
        definition.setMeaning(meaning);
        definition.setSynonyms(List.of("clear"));

        EntryQueryResponse.EnglishWordPayload payload = new EntryQueryResponse.EnglishWordPayload();
        payload.setWord(word);
        payload.setDefinitions(List.of(definition));
        payload.setExamples(List.of());
        response.setEnglishWord(payload);
        return response;
    }

    private EntryQueryResponse zhToEnResponse(String input, String term, String coreMeaning) {
        EntryQueryResponse response = new EntryQueryResponse();
        response.setQuery(input);
        response.setNormalizedQuery(input);
        response.setResultType(EntryResultType.ZH_TO_EN_TERM);
        response.setSourceLanguage("zh");
        response.setTargetLanguage("en");

        EntryQueryResponse.TermCandidate candidate = new EntryQueryResponse.TermCandidate();
        candidate.setTerm(term);
        candidate.setCoreMeaning(coreMeaning);

        EntryQueryResponse.ZhToEnTermPayload payload = new EntryQueryResponse.ZhToEnTermPayload();
        payload.setInput(input);
        payload.setCandidates(List.of(candidate));
        response.setZhToEnTerm(payload);
        return response;
    }
}

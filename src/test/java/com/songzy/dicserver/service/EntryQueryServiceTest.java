package com.songzy.dicserver.service;

import com.songzy.dicserver.exception.BusinessException;
import com.songzy.dicserver.model.AudioGenerationStatus;
import com.songzy.dicserver.model.AudioGenerationTask;
import com.songzy.dicserver.model.AudioType;
import com.songzy.dicserver.model.EntryQueryResponse;
import com.songzy.dicserver.model.EntryResultType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntryQueryServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private EntryCacheService entryCacheService;

    @Mock
    private EntryPromptBuilder entryPromptBuilder;

    @Mock
    private LlmService llmService;

    @Mock
    private EntryResponseParser entryResponseParser;

    @Mock
    private EntryAudioService entryAudioService;

    @Mock
    private QueryTextNormalizer queryTextNormalizer;

    @Mock
    private QueryHistoryService queryHistoryService;

    @Mock
    private QueryResultSnapshotService queryResultSnapshotService;

    @InjectMocks
    private EntryQueryService entryQueryService;

    @BeforeEach
    void setUp() {
        lenient().when(queryTextNormalizer.normalizeForStorage(anyString()))
                .thenAnswer(invocation -> normalizeForStorage(invocation.getArgument(0)));
        lenient().when(queryResultSnapshotService.findEntrySnapshot(anyString(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void queryShouldUseRequestedTypeWhenProvided() {
        EntryQueryResponse response = englishWordResponse("hello");
        EntryQueryResponse.HeadwordAudio headwordAudio = new EntryQueryResponse.HeadwordAudio();
        headwordAudio.setAudioKey("english_word:headword:hello");
        headwordAudio.setAudioUrl("/api/v1/audio/by-key/english_word%3Aheadword%3Ahello");
        response.getEnglishWord().setHeadwordAudio(headwordAudio);

        EntryQueryResponse.HeadwordAudio fullReadingAudio = new EntryQueryResponse.HeadwordAudio();
        fullReadingAudio.setAudioKey("english_word:full_reading:hello");
        fullReadingAudio.setAudioUrl("/api/v1/audio/by-key/english_word%3Afull_reading%3Ahello");
        response.getEnglishWord().setFullReadingAudio(fullReadingAudio);

        when(entryAudioService.enrichHeadwordAudio(response)).thenReturn(response);
        when(entryCacheService.buildCacheKey(EntryResultType.ENGLISH_WORD, "hello"))
                .thenReturn("ENGLISH_WORD:hello");
        when(entryCacheService.get("ENGLISH_WORD:hello")).thenReturn(Optional.empty());
        when(entryPromptBuilder.buildPrompt(EntryResultType.ENGLISH_WORD)).thenReturn("prompt");
        when(llmService.queryRawJson("hello", "prompt")).thenReturn("{\"resultType\":\"ENGLISH_WORD\"}");
        when(entryResponseParser.parse("{\"resultType\":\"ENGLISH_WORD\"}", EntryResultType.ENGLISH_WORD))
                .thenReturn(response);

        EntryQueryResponse result = entryQueryService.query(USER_ID, "  hello  ", EntryResultType.ENGLISH_WORD);

        assertThat(result).isSameAs(response);
        assertThat(result.getEnglishWord().getHeadwordAudio()).isNotNull();
        assertThat(result.getEnglishWord().getFullReadingAudio()).isNotNull();
        verify(entryPromptBuilder).buildPrompt(EntryResultType.ENGLISH_WORD);
        verify(llmService).queryRawJson("hello", "prompt");
        verify(entryCacheService).put("ENGLISH_WORD:hello", response);
    }

    @Test
    void queryShouldPersistBeforeCacheMutationOnFreshResult() {
        EntryQueryResponse response = englishWordResponse("hello");
        when(entryAudioService.enrichHeadwordAudio(response)).thenReturn(response);
        when(entryCacheService.buildCacheKey(EntryResultType.ENGLISH_WORD, "hello"))
                .thenReturn("ENGLISH_WORD:hello");
        when(entryCacheService.get("ENGLISH_WORD:hello")).thenReturn(Optional.empty());
        when(entryPromptBuilder.buildPrompt(EntryResultType.ENGLISH_WORD)).thenReturn("prompt");
        when(llmService.queryRawJson("hello", "prompt")).thenReturn("{\"resultType\":\"ENGLISH_WORD\"}");
        when(entryResponseParser.parse("{\"resultType\":\"ENGLISH_WORD\"}", EntryResultType.ENGLISH_WORD))
                .thenReturn(response);

        entryQueryService.query(USER_ID, "hello", EntryResultType.ENGLISH_WORD);

        InOrder inOrder = inOrder(queryHistoryService, queryResultSnapshotService, entryCacheService);
        inOrder.verify(queryHistoryService).recordSuccessWithSnapshot(
                USER_ID,
                "hello",
                "hello",
                response,
                true
        );
        inOrder.verify(queryResultSnapshotService).upsertEntrySnapshot("hello", response);
        inOrder.verify(entryCacheService).put("ENGLISH_WORD:hello", response);
    }

    @Test
    void queryShouldReuseSharedSnapshotAndStillRecordCurrentUsersHistory() {
        EntryQueryResponse persisted = englishWordResponse("visible");
        when(entryCacheService.buildCacheKey(EntryResultType.ENGLISH_WORD, "visible"))
                .thenReturn("ENGLISH_WORD:visible");
        when(entryCacheService.get("ENGLISH_WORD:visible")).thenReturn(Optional.empty());
        when(queryResultSnapshotService.findEntrySnapshot("visible", EntryResultType.ENGLISH_WORD))
                .thenReturn(Optional.of(persisted));
        when(entryAudioService.enrichHeadwordAudio(persisted)).thenReturn(persisted);

        EntryQueryResponse response = entryQueryService.query(USER_ID, "visible", EntryResultType.ENGLISH_WORD);

        assertThat(response.getEnglishWord().getWord()).isEqualTo("visible");
        verify(queryHistoryService).recordSuccessWithSnapshot(USER_ID, "visible", "visible", persisted, false);
        verify(queryResultSnapshotService, never()).upsertEntrySnapshot(anyString(), any());
        verify(entryCacheService).put("ENGLISH_WORD:visible", persisted);
        verify(llmService, never()).queryRawJson(any(), any());
    }

    @Test
    void queryShouldRecordHistoryWhenFreshEntriesQuerySucceeds() {
        EntryQueryResponse response = englishWordResponse("visible");
        when(entryAudioService.enrichHeadwordAudio(response)).thenReturn(response);
        when(entryCacheService.buildCacheKey(EntryResultType.ENGLISH_WORD, "visible"))
                .thenReturn("ENGLISH_WORD:visible");
        when(entryCacheService.get("ENGLISH_WORD:visible")).thenReturn(Optional.empty());
        when(entryPromptBuilder.buildPrompt(EntryResultType.ENGLISH_WORD)).thenReturn("prompt");
        when(llmService.queryRawJson("visible", "prompt")).thenReturn("{\"resultType\":\"ENGLISH_WORD\"}");
        when(entryResponseParser.parse("{\"resultType\":\"ENGLISH_WORD\"}", EntryResultType.ENGLISH_WORD))
                .thenReturn(response);

        entryQueryService.query(USER_ID, "  visible  ", EntryResultType.ENGLISH_WORD);

        verify(queryHistoryService).recordSuccessWithSnapshot(
                USER_ID,
                "  visible  ",
                "visible",
                response,
                true
        );
    }

    @Test
    void queryShouldNotMutateCacheWhenPersistenceFails() {
        EntryQueryResponse response = englishWordResponse("hello");
        when(entryAudioService.enrichHeadwordAudio(response)).thenReturn(response);
        when(entryCacheService.buildCacheKey(EntryResultType.ENGLISH_WORD, "hello"))
                .thenReturn("ENGLISH_WORD:hello");
        when(entryCacheService.get("ENGLISH_WORD:hello")).thenReturn(Optional.empty());
        when(entryPromptBuilder.buildPrompt(EntryResultType.ENGLISH_WORD)).thenReturn("prompt");
        when(llmService.queryRawJson("hello", "prompt")).thenReturn("{\"resultType\":\"ENGLISH_WORD\"}");
        when(entryResponseParser.parse("{\"resultType\":\"ENGLISH_WORD\"}", EntryResultType.ENGLISH_WORD))
                .thenReturn(response);
        doThrow(new IllegalStateException("persist failed"))
                .when(queryHistoryService)
                .recordSuccessWithSnapshot(USER_ID, "hello", "hello", response, true);

        assertThatThrownBy(() -> entryQueryService.query(USER_ID, "hello", EntryResultType.ENGLISH_WORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("persist failed");
        verify(entryCacheService, never()).put("ENGLISH_WORD:hello", response);
        verify(entryCacheService, never()).invalidate(anyString());
    }

    @Test
    void queryShouldNotMutateCachedInstanceWhenCacheHitPersistenceFails() {
        EntryQueryResponse cached = englishWordResponse("hello");
        when(entryCacheService.buildCacheKey(EntryResultType.ENGLISH_WORD, "hello"))
                .thenReturn("ENGLISH_WORD:hello");
        when(entryCacheService.get("ENGLISH_WORD:hello")).thenReturn(Optional.of(cached));
        when(entryAudioService.enrichHeadwordAudio(any(EntryQueryResponse.class))).thenAnswer(invocation -> {
            EntryQueryResponse response = invocation.getArgument(0);
            EntryQueryResponse.HeadwordAudio audio = new EntryQueryResponse.HeadwordAudio();
            audio.setAudioKey("english_word:headword:hello");
            audio.setAudioUrl("/api/v1/audio/by-key/english_word%3Aheadword%3Ahello");
            response.getEnglishWord().setHeadwordAudio(audio);
            return response;
        });
        doThrow(new IllegalStateException("persist failed"))
                .when(queryHistoryService)
                .recordSuccessWithSnapshot(
                        eq(USER_ID),
                        eq("hello"),
                        eq("hello"),
                        any(EntryQueryResponse.class),
                        eq(false)
                );

        assertThatThrownBy(() -> entryQueryService.query(USER_ID, "hello", EntryResultType.ENGLISH_WORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("persist failed");

        assertThat(cached.getEnglishWord().getHeadwordAudio()).isNull();
        assertThat(cached.getEnglishWord().getFullReadingAudio()).isNull();
        verify(entryCacheService, never()).put(anyString(), any(EntryQueryResponse.class));
    }

    @Test
    void refreshShouldPersistBeforeCacheOverwrite() {
        EntryQueryResponse response = englishWordResponse("hello");
        when(entryAudioService.enrichHeadwordAudio(response)).thenReturn(response);
        when(entryCacheService.buildCacheKey(EntryResultType.ENGLISH_WORD, "hello"))
                .thenReturn("ENGLISH_WORD:hello");
        when(entryPromptBuilder.buildPrompt(EntryResultType.ENGLISH_WORD)).thenReturn("prompt");
        when(llmService.queryRawJson("hello", "prompt")).thenReturn("{\"resultType\":\"ENGLISH_WORD\"}");
        when(entryResponseParser.parse("{\"resultType\":\"ENGLISH_WORD\"}", EntryResultType.ENGLISH_WORD))
                .thenReturn(response);

        entryQueryService.refresh(USER_ID, "hello", EntryResultType.ENGLISH_WORD);

        verify(entryCacheService, never()).get(anyString());
        InOrder inOrder = inOrder(queryHistoryService, entryCacheService);
        inOrder.verify(queryHistoryService).recordSuccessWithSnapshot(
                USER_ID,
                "hello",
                "hello",
                response,
                true
        );
        inOrder.verify(entryCacheService).put("ENGLISH_WORD:hello", response);
    }

    @Test
    void refreshShouldNotMutateCacheWhenPersistenceFails() {
        EntryQueryResponse response = englishWordResponse("hello");
        when(entryAudioService.enrichHeadwordAudio(response)).thenReturn(response);
        when(entryCacheService.buildCacheKey(EntryResultType.ENGLISH_WORD, "hello"))
                .thenReturn("ENGLISH_WORD:hello");
        when(entryPromptBuilder.buildPrompt(EntryResultType.ENGLISH_WORD)).thenReturn("prompt");
        when(llmService.queryRawJson("hello", "prompt")).thenReturn("{\"resultType\":\"ENGLISH_WORD\"}");
        when(entryResponseParser.parse("{\"resultType\":\"ENGLISH_WORD\"}", EntryResultType.ENGLISH_WORD))
                .thenReturn(response);
        doThrow(new IllegalStateException("persist failed"))
                .when(queryHistoryService)
                .recordSuccessWithSnapshot(USER_ID, "hello", "hello", response, true);

        assertThatThrownBy(() -> entryQueryService.refresh(USER_ID, "hello", EntryResultType.ENGLISH_WORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("persist failed");
        verify(entryCacheService, never()).put("ENGLISH_WORD:hello", response);
        verify(entryCacheService, never()).invalidate(anyString());
    }

    @Test
    void queryShouldAllowLlmSelectedTypeWhenRequestedTypeMissing() {
        EntryQueryResponse response = responseOfType(EntryResultType.ZH_TO_EN_TERM);
        when(entryAudioService.enrichHeadwordAudio(response)).thenReturn(response);
        when(entryPromptBuilder.buildPrompt(null)).thenReturn("prompt");
        when(llmService.queryRawJson("苹果", "prompt")).thenReturn("{\"resultType\":\"ZH_TO_EN_TERM\"}");
        when(entryResponseParser.parse("{\"resultType\":\"ZH_TO_EN_TERM\"}", null)).thenReturn(response);
        when(entryCacheService.buildCacheKey(EntryResultType.ZH_TO_EN_TERM, "苹果"))
                .thenReturn("ZH_TO_EN_TERM:苹果");

        EntryQueryResponse result = entryQueryService.query(USER_ID, " 苹果 ", null);

        assertThat(result).isSameAs(response);
        verify(entryCacheService, never()).get(anyString());
        verify(entryCacheService).put("ZH_TO_EN_TERM:苹果", response);
    }

    @Test
    void queryShouldReturnCachedResponseWhenPresent() {
        EntryQueryResponse cached = englishWordResponse("hello");
        EntryQueryResponse.HeadwordAudio headwordAudio = new EntryQueryResponse.HeadwordAudio();
        headwordAudio.setAudioKey("english_word:headword:hello");
        headwordAudio.setAudioUrl("/api/v1/audio/by-key/english_word%3Aheadword%3Ahello");
        cached.getEnglishWord().setHeadwordAudio(headwordAudio);

        EntryQueryResponse.HeadwordAudio fullReadingAudio = new EntryQueryResponse.HeadwordAudio();
        fullReadingAudio.setAudioKey("english_word:full_reading:hello");
        fullReadingAudio.setAudioUrl("/api/v1/audio/by-key/english_word%3Afull_reading%3Ahello");
        cached.getEnglishWord().setFullReadingAudio(fullReadingAudio);

        when(entryAudioService.enrichHeadwordAudio(cached)).thenReturn(cached);
        when(entryCacheService.buildCacheKey(EntryResultType.ENGLISH_WORD, "Hello"))
                .thenReturn("ENGLISH_WORD:hello");
        when(entryCacheService.get("ENGLISH_WORD:hello")).thenReturn(Optional.of(cached));

        EntryQueryResponse result = entryQueryService.query(USER_ID, " Hello ", EntryResultType.ENGLISH_WORD);

        assertThat(result).isSameAs(cached);
        assertThat(result.getEnglishWord().getHeadwordAudio()).isNotNull();
        assertThat(result.getEnglishWord().getFullReadingAudio()).isNotNull();
        verify(llmService, never()).queryRawJson(anyString(), anyString());
        verify(entryPromptBuilder, never()).buildPrompt(EntryResultType.ENGLISH_WORD);
        verify(entryAudioService).enrichHeadwordAudio(cached);
        verify(entryCacheService).put("ENGLISH_WORD:hello", cached);
    }

    @Test
    void queryShouldRecordHistoryWhenCacheHitReturnsResponse() {
        EntryQueryResponse cached = englishWordResponse("visible");
        when(entryCacheService.buildCacheKey(EntryResultType.ENGLISH_WORD, "visible"))
                .thenReturn("ENGLISH_WORD:visible");
        when(entryCacheService.get("ENGLISH_WORD:visible")).thenReturn(Optional.of(cached));
        when(entryAudioService.enrichHeadwordAudio(cached)).thenReturn(cached);

        entryQueryService.query(USER_ID, " visible ", EntryResultType.ENGLISH_WORD);

        verify(queryHistoryService).recordSuccessWithSnapshot(
                USER_ID,
                " visible ",
                "visible",
                cached,
                false
        );
    }

    @Test
    void queryShouldUpsertSnapshotFromFreshEnglishWordResult() {
        EntryQueryResponse response = englishWordResponse("visible");
        when(entryAudioService.enrichHeadwordAudio(response)).thenReturn(response);
        when(entryCacheService.buildCacheKey(EntryResultType.ENGLISH_WORD, "visible"))
                .thenReturn("ENGLISH_WORD:visible");
        when(entryCacheService.get("ENGLISH_WORD:visible")).thenReturn(Optional.empty());
        when(entryPromptBuilder.buildPrompt(EntryResultType.ENGLISH_WORD)).thenReturn("prompt");
        when(llmService.queryRawJson("visible", "prompt")).thenReturn("{\"resultType\":\"ENGLISH_WORD\"}");
        when(entryResponseParser.parse("{\"resultType\":\"ENGLISH_WORD\"}", EntryResultType.ENGLISH_WORD))
                .thenReturn(response);

        entryQueryService.query(USER_ID, "visible", EntryResultType.ENGLISH_WORD);

        verify(queryHistoryService).recordSuccessWithSnapshot(
                USER_ID,
                "visible",
                "visible",
                response,
                true
        );
    }

    @Test
    void queryShouldBackfillSnapshotWithoutFreshUpsertOnEnglishWordCacheHit() {
        EntryQueryResponse cached = englishWordResponse("visible");
        when(entryCacheService.buildCacheKey(EntryResultType.ENGLISH_WORD, "visible"))
                .thenReturn("ENGLISH_WORD:visible");
        when(entryCacheService.get("ENGLISH_WORD:visible")).thenReturn(Optional.of(cached));
        when(entryAudioService.enrichHeadwordAudio(cached)).thenReturn(cached);

        entryQueryService.query(USER_ID, "visible", EntryResultType.ENGLISH_WORD);

        verify(queryHistoryService).recordSuccessWithSnapshot(
                USER_ID,
                "visible",
                "visible",
                cached,
                false
        );
    }

    @Test
    void queryShouldNotWriteSnapshotsForNonEnglishResultTypes() {
        EntryQueryResponse zhToEnResult = zhToEnResponse("ice cream");
        when(entryPromptBuilder.buildPrompt(null)).thenReturn("prompt", "prompt-2");
        when(llmService.queryRawJson("冰淇淋", "prompt")).thenReturn("{\"resultType\":\"ZH_TO_EN_TERM\"}");
        when(entryResponseParser.parse("{\"resultType\":\"ZH_TO_EN_TERM\"}", null)).thenReturn(zhToEnResult);
        when(entryAudioService.enrichHeadwordAudio(zhToEnResult)).thenReturn(zhToEnResult);
        when(entryCacheService.buildCacheKey(EntryResultType.ZH_TO_EN_TERM, "冰淇淋"))
                .thenReturn("ZH_TO_EN_TERM:冰淇淋");

        EntryQueryResponse sentenceResult = responseOfType(EntryResultType.SENTENCE_TRANSLATION);
        EntryQueryResponse.SentenceTranslationPayload sentencePayload = new EntryQueryResponse.SentenceTranslationPayload();
        sentencePayload.setSourceSentence("你好");
        sentencePayload.setTranslatedSentence("hello");
        sentenceResult.setSentenceTranslation(sentencePayload);
        when(llmService.queryRawJson("你好", "prompt-2")).thenReturn("{\"resultType\":\"SENTENCE_TRANSLATION\"}");
        when(entryResponseParser.parse("{\"resultType\":\"SENTENCE_TRANSLATION\"}", null)).thenReturn(sentenceResult);
        when(entryCacheService.buildCacheKey(EntryResultType.SENTENCE_TRANSLATION, "你好"))
                .thenReturn("SENTENCE_TRANSLATION:你好");

        entryQueryService.query(USER_ID, "冰淇淋", null);
        entryQueryService.query(USER_ID, "你好", null);

        verify(queryHistoryService).recordSuccessWithSnapshot(
                USER_ID,
                "冰淇淋",
                "冰淇淋",
                zhToEnResult,
                true
        );
        verify(queryHistoryService).recordSuccessWithSnapshot(
                USER_ID,
                "你好",
                "你好",
                sentenceResult,
                true
        );
    }

    @Test
    void queryShouldRefreshAudioWhenCachedResponseCanNowBeEnriched() {
        EntryQueryResponse cached = englishWordResponse("hello");
        EntryQueryResponse enriched = englishWordResponse("hello");
        EntryQueryResponse.HeadwordAudio headwordAudio = new EntryQueryResponse.HeadwordAudio();
        headwordAudio.setAudioKey("english_word:headword:hello");
        headwordAudio.setAudioUrl("/api/v1/audio/by-key/english_word%3Aheadword%3Ahello");
        enriched.getEnglishWord().setHeadwordAudio(headwordAudio);
        EntryQueryResponse.HeadwordAudio fullReadingAudio = new EntryQueryResponse.HeadwordAudio();
        fullReadingAudio.setAudioKey("english_word:full_reading:hello");
        fullReadingAudio.setAudioUrl("/api/v1/audio/by-key/english_word%3Afull_reading%3Ahello");
        enriched.getEnglishWord().setFullReadingAudio(fullReadingAudio);

        when(entryCacheService.buildCacheKey(EntryResultType.ENGLISH_WORD, "hello"))
                .thenReturn("ENGLISH_WORD:hello");
        when(entryCacheService.get("ENGLISH_WORD:hello")).thenReturn(Optional.of(cached));
        when(entryAudioService.enrichHeadwordAudio(cached)).thenReturn(enriched);

        EntryQueryResponse result = entryQueryService.query(USER_ID, "hello", EntryResultType.ENGLISH_WORD);

        assertThat(result.getEnglishWord().getHeadwordAudio()).isNotNull();
        assertThat(result.getEnglishWord().getHeadwordAudio().getAudioKey())
                .isEqualTo("english_word:headword:hello");
        assertThat(result.getEnglishWord().getFullReadingAudio()).isNotNull();
        assertThat(result.getEnglishWord().getFullReadingAudio().getAudioKey())
                .isEqualTo("english_word:full_reading:hello");
        verify(llmService, never()).queryRawJson(anyString(), anyString());
        verify(entryCacheService).put("ENGLISH_WORD:hello", enriched);
    }

    @Test
    void queryShouldThrowWhenExplicitTypeDoesNotMatchReturnedType() {
        when(entryCacheService.buildCacheKey(EntryResultType.ENGLISH_WORD, "hello"))
                .thenReturn("ENGLISH_WORD:hello");
        when(entryCacheService.get("ENGLISH_WORD:hello")).thenReturn(Optional.empty());
        when(entryPromptBuilder.buildPrompt(EntryResultType.ENGLISH_WORD)).thenReturn("prompt");
        when(llmService.queryRawJson("hello", "prompt")).thenReturn("{\"resultType\":\"ZH_TO_EN_TERM\"}");
        when(entryResponseParser.parse("{\"resultType\":\"ZH_TO_EN_TERM\"}", EntryResultType.ENGLISH_WORD))
                .thenThrow(new BusinessException("返回结果类型与请求类型不匹配", "LLM_RESPONSE_INVALID"));

        assertThatThrownBy(() -> entryQueryService.query(USER_ID, "hello", EntryResultType.ENGLISH_WORD))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("LLM_RESPONSE_INVALID");

        verify(entryCacheService, never()).put(anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void queryShouldAttachEnglishWordAudioWhenEnrichmentSucceeds() {
        EntryQueryResponse parsed = englishWordResponse("hello");
        EntryQueryResponse enriched = englishWordResponse("hello");
        EntryQueryResponse.HeadwordAudio headwordAudio = new EntryQueryResponse.HeadwordAudio();
        headwordAudio.setAudioKey("english_word:headword:hello");
        headwordAudio.setAudioUrl("/api/v1/audio/by-key/english_word%3Aheadword%3Ahello");
        enriched.getEnglishWord().setHeadwordAudio(headwordAudio);
        EntryQueryResponse.HeadwordAudio fullReadingAudio = new EntryQueryResponse.HeadwordAudio();
        fullReadingAudio.setAudioKey("english_word:full_reading:hello");
        fullReadingAudio.setAudioUrl("/api/v1/audio/by-key/english_word%3Afull_reading%3Ahello");
        enriched.getEnglishWord().setFullReadingAudio(fullReadingAudio);

        when(entryCacheService.buildCacheKey(EntryResultType.ENGLISH_WORD, "hello"))
                .thenReturn("ENGLISH_WORD:hello");
        when(entryCacheService.get("ENGLISH_WORD:hello")).thenReturn(Optional.empty());
        when(entryPromptBuilder.buildPrompt(EntryResultType.ENGLISH_WORD)).thenReturn("prompt");
        when(llmService.queryRawJson("hello", "prompt")).thenReturn("{\"resultType\":\"ENGLISH_WORD\"}");
        when(entryResponseParser.parse("{\"resultType\":\"ENGLISH_WORD\"}", EntryResultType.ENGLISH_WORD))
                .thenReturn(parsed);
        when(entryAudioService.enrichHeadwordAudio(parsed)).thenReturn(enriched);

        EntryQueryResponse result = entryQueryService.query(USER_ID, "hello", EntryResultType.ENGLISH_WORD);

        assertThat(result.getEnglishWord().getHeadwordAudio()).isNotNull();
        assertThat(result.getEnglishWord().getHeadwordAudio().getAudioKey())
                .isEqualTo("english_word:headword:hello");
        assertThat(result.getEnglishWord().getFullReadingAudio()).isNotNull();
        assertThat(result.getEnglishWord().getFullReadingAudio().getAudioKey())
                .isEqualTo("english_word:full_reading:hello");
    }

    @Test
    void queryShouldAttachZhToEnCandidateAudioWhenEnrichmentSucceeds() {
        EntryQueryResponse parsed = zhToEnResponse("ice cream");
        EntryQueryResponse enriched = zhToEnResponse("ice cream");
        EntryQueryResponse.HeadwordAudio headwordAudio = new EntryQueryResponse.HeadwordAudio();
        headwordAudio.setAudioKey("zh_to_en_term:headword:ice cream");
        headwordAudio.setAudioUrl("/api/v1/audio/by-key/zh_to_en_term%3Aheadword%3Aice%20cream");
        enriched.getZhToEnTerm().getCandidates().getFirst().setHeadwordAudio(headwordAudio);

        when(entryPromptBuilder.buildPrompt(null)).thenReturn("prompt");
        when(llmService.queryRawJson("冰淇淋", "prompt")).thenReturn("{\"resultType\":\"ZH_TO_EN_TERM\"}");
        when(entryResponseParser.parse("{\"resultType\":\"ZH_TO_EN_TERM\"}", null)).thenReturn(parsed);
        when(entryAudioService.enrichHeadwordAudio(parsed)).thenReturn(enriched);
        when(entryCacheService.buildCacheKey(EntryResultType.ZH_TO_EN_TERM, "冰淇淋"))
                .thenReturn("ZH_TO_EN_TERM:冰淇淋");

        EntryQueryResponse result = entryQueryService.query(USER_ID, "冰淇淋", null);

        assertThat(result.getZhToEnTerm().getCandidates().getFirst().getHeadwordAudio()).isNotNull();
        assertThat(result.getZhToEnTerm().getCandidates().getFirst().getHeadwordAudio().getAudioKey())
                .isEqualTo("zh_to_en_term:headword:ice cream");
    }

    @Test
    void queryShouldNotEnrichSentenceTranslationAudio() {
        EntryQueryResponse response = responseOfType(EntryResultType.SENTENCE_TRANSLATION);
        EntryQueryResponse.SentenceTranslationPayload payload = new EntryQueryResponse.SentenceTranslationPayload();
        payload.setSourceSentence("你好");
        response.setSentenceTranslation(payload);
        when(entryPromptBuilder.buildPrompt(null)).thenReturn("prompt");
        when(llmService.queryRawJson("你好", "prompt")).thenReturn("{\"resultType\":\"SENTENCE_TRANSLATION\"}");
        when(entryResponseParser.parse("{\"resultType\":\"SENTENCE_TRANSLATION\"}", null)).thenReturn(response);
        when(entryCacheService.buildCacheKey(EntryResultType.SENTENCE_TRANSLATION, "你好"))
                .thenReturn("SENTENCE_TRANSLATION:你好");

        EntryQueryResponse result = entryQueryService.query(USER_ID, "你好", null);

        assertThat(result.getSentenceTranslation()).isNotNull();
        verify(entryAudioService, never()).enrichHeadwordAudio(response);
    }

    @Test
    void queryShouldKeepAudioReferencesWhenEnrichmentRegistersTasksInsteadOfReturningNull() {
        EntryQueryResponse parsed = englishWordResponse("hello");
        EntryQueryResponse enriched = englishWordResponse("hello");
        EntryQueryResponse.HeadwordAudio headwordAudio = new EntryQueryResponse.HeadwordAudio();
        headwordAudio.setAudioKey("english_word:headword:hello");
        headwordAudio.setAudioUrl("/api/v1/audio/by-key/english_word%3Aheadword%3Ahello");
        enriched.getEnglishWord().setHeadwordAudio(headwordAudio);
        EntryQueryResponse.HeadwordAudio fullReadingAudio = new EntryQueryResponse.HeadwordAudio();
        fullReadingAudio.setAudioKey("english_word:full_reading:hello");
        fullReadingAudio.setAudioUrl("/api/v1/audio/by-key/english_word%3Afull_reading%3Ahello");
        enriched.getEnglishWord().setFullReadingAudio(fullReadingAudio);

        when(entryCacheService.buildCacheKey(EntryResultType.ENGLISH_WORD, "hello"))
                .thenReturn("ENGLISH_WORD:hello");
        when(entryCacheService.get("ENGLISH_WORD:hello")).thenReturn(Optional.empty());
        when(entryPromptBuilder.buildPrompt(EntryResultType.ENGLISH_WORD)).thenReturn("prompt");
        when(llmService.queryRawJson("hello", "prompt")).thenReturn("{\"resultType\":\"ENGLISH_WORD\"}");
        when(entryResponseParser.parse("{\"resultType\":\"ENGLISH_WORD\"}", EntryResultType.ENGLISH_WORD))
                .thenReturn(parsed);
        when(entryAudioService.enrichHeadwordAudio(parsed)).thenReturn(enriched);

        EntryQueryResponse result = entryQueryService.query(USER_ID, "hello", EntryResultType.ENGLISH_WORD);

        assertThat(result.getEnglishWord().getHeadwordAudio()).isNotNull();
        assertThat(result.getEnglishWord().getFullReadingAudio()).isNotNull();
    }

    @Test
    void enrichHeadwordAudioShouldAttachEnglishWordReferencesAndEnsureTasksWhenAudioMissing() {
        AudioStorageService audioStorageService = org.mockito.Mockito.mock(AudioStorageService.class);
        AudioGenerationTaskService taskService = org.mockito.Mockito.mock(AudioGenerationTaskService.class);
        AudioKeyBuilder audioKeyBuilder = new AudioKeyBuilder();
        AudioGenerationExecutor executor = org.mockito.Mockito.mock(AudioGenerationExecutor.class);
        TtsService ttsService = org.mockito.Mockito.mock(TtsService.class);
        EntryAudioService audioService = new EntryAudioService(audioStorageService, audioKeyBuilder, taskService, executor, ttsService);
        EntryQueryResponse response = englishWordResponse("  Hello  World  ");
        response.getEnglishWord().setDefinitions(List.of());
        response.getEnglishWord().setExamples(List.of());

        when(audioStorageService.loadAudioByKey("english_word:headword:hello world")).thenReturn(null);
        when(audioStorageService.loadAudioByKey("english_word:full_reading:hello world")).thenReturn(null);
        when(ttsService.buildReadingText(eq("Hello World"), any())).thenReturn("full reading");
        when(executor.execute("english_word:headword:hello world")).thenReturn(AudioGenerationExecutor.ExecutionResult.READY);
        when(executor.execute("english_word:full_reading:hello world")).thenReturn(AudioGenerationExecutor.ExecutionResult.READY);

        EntryQueryResponse enriched = audioService.enrichHeadwordAudio(response);

        assertThat(enriched.getEnglishWord().getHeadwordAudio()).isNotNull();
        assertThat(enriched.getEnglishWord().getHeadwordAudio().getAudioKey())
                .isEqualTo("english_word:headword:hello world");
        assertThat(enriched.getEnglishWord().getHeadwordAudio().getAudioUrl())
                .isEqualTo("/api/v1/audio/by-key/english_word%3Aheadword%3Ahello%20world");
        assertThat(enriched.getEnglishWord().getFullReadingAudio()).isNotNull();
        assertThat(enriched.getEnglishWord().getFullReadingAudio().getAudioKey())
                .isEqualTo("english_word:full_reading:hello world");
        assertThat(enriched.getEnglishWord().getFullReadingAudio().getAudioUrl())
                .isEqualTo("/api/v1/audio/by-key/english_word%3Afull_reading%3Ahello%20world");

        ArgumentCaptor<AudioGenerationTask> taskCaptor = ArgumentCaptor.forClass(AudioGenerationTask.class);
        verify(taskService, times(2)).ensureTask(taskCaptor.capture());
        assertThat(taskCaptor.getAllValues())
                .extracting(AudioGenerationTask::getAudioKey, AudioGenerationTask::getAudioType,
                        AudioGenerationTask::getSourceType, AudioGenerationTask::getSourceText)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(
                                "english_word:headword:hello world",
                                AudioType.HEADWORD,
                                EntryResultType.ENGLISH_WORD,
                                "Hello World"
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                "english_word:full_reading:hello world",
                                AudioType.FULL_READING,
                                EntryResultType.ENGLISH_WORD,
                                "full reading"
                        )
                );
        verify(executor, timeout(1000)).execute("english_word:headword:hello world");
        verify(executor, timeout(1000)).execute("english_word:full_reading:hello world");
    }

    @Test
    void enrichHeadwordAudioShouldAttachZhToEnCandidateReferencesAndEnsureTasksWhenAudioMissing() {
        AudioStorageService audioStorageService = org.mockito.Mockito.mock(AudioStorageService.class);
        AudioGenerationTaskService taskService = org.mockito.Mockito.mock(AudioGenerationTaskService.class);
        AudioKeyBuilder audioKeyBuilder = new AudioKeyBuilder();
        AudioGenerationExecutor executor = org.mockito.Mockito.mock(AudioGenerationExecutor.class);
        TtsService ttsService = org.mockito.Mockito.mock(TtsService.class);
        EntryAudioService audioService = new EntryAudioService(audioStorageService, audioKeyBuilder, taskService, executor, ttsService);
        EntryQueryResponse response = zhToEnResponse("  Ice   Cream  ");

        when(audioStorageService.loadAudioByKey("zh_to_en_term:headword:ice cream")).thenReturn(null);

        EntryQueryResponse enriched = audioService.enrichHeadwordAudio(response);

        assertThat(enriched.getZhToEnTerm().getCandidates()).hasSize(1);
        assertThat(enriched.getZhToEnTerm().getCandidates().getFirst().getHeadwordAudio()).isNotNull();
        assertThat(enriched.getZhToEnTerm().getCandidates().getFirst().getHeadwordAudio().getAudioKey())
                .isEqualTo("zh_to_en_term:headword:ice cream");
        assertThat(enriched.getZhToEnTerm().getCandidates().getFirst().getHeadwordAudio().getAudioUrl())
                .isEqualTo("/api/v1/audio/by-key/zh_to_en_term%3Aheadword%3Aice%20cream");

        ArgumentCaptor<AudioGenerationTask> taskCaptor = ArgumentCaptor.forClass(AudioGenerationTask.class);
        verify(taskService).ensureTask(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getAudioKey()).isEqualTo("zh_to_en_term:headword:ice cream");
        assertThat(taskCaptor.getValue().getAudioType()).isEqualTo(AudioType.HEADWORD);
        assertThat(taskCaptor.getValue().getSourceType()).isEqualTo(EntryResultType.ZH_TO_EN_TERM);
        assertThat(taskCaptor.getValue().getSourceText()).isEqualTo("Ice Cream");
        verify(executor, timeout(1000)).execute("zh_to_en_term:headword:ice cream");
    }

    @Test
    void enrichHeadwordAudioShouldKeepEnglishWordMetadataWhenTaskRegistrationFails() {
        AudioStorageService audioStorageService = org.mockito.Mockito.mock(AudioStorageService.class);
        AudioGenerationTaskService taskService = org.mockito.Mockito.mock(AudioGenerationTaskService.class);
        AudioKeyBuilder audioKeyBuilder = new AudioKeyBuilder();
        AudioGenerationExecutor executor = org.mockito.Mockito.mock(AudioGenerationExecutor.class);
        TtsService ttsService = org.mockito.Mockito.mock(TtsService.class);
        EntryAudioService audioService = new EntryAudioService(audioStorageService, audioKeyBuilder, taskService, executor, ttsService);
        EntryQueryResponse response = englishWordResponse("hello");
        response.getEnglishWord().setDefinitions(List.of());
        response.getEnglishWord().setExamples(List.of());

        when(audioStorageService.loadAudioByKey("english_word:headword:hello")).thenReturn(null);
        when(audioStorageService.loadAudioByKey("english_word:full_reading:hello")).thenReturn(null);
        doThrow(new RuntimeException("task registration failed")).when(taskService).ensureTask(any(AudioGenerationTask.class));
        when(ttsService.buildReadingText(eq("hello"), any())).thenReturn("full reading");

        EntryQueryResponse enriched = audioService.enrichHeadwordAudio(response);

        assertThat(enriched).isSameAs(response);
        assertThat(enriched.getResultType()).isEqualTo(EntryResultType.ENGLISH_WORD);
        assertThat(enriched.getEnglishWord()).isNotNull();
        assertThat(enriched.getEnglishWord().getWord()).isEqualTo("hello");
        assertThat(enriched.getEnglishWord().getHeadwordAudio()).isNotNull();
        assertThat(enriched.getEnglishWord().getHeadwordAudio().getAudioKey())
                .isEqualTo("english_word:headword:hello");
        assertThat(enriched.getEnglishWord().getHeadwordAudio().getAudioUrl())
                .isEqualTo("/api/v1/audio/by-key/english_word%3Aheadword%3Ahello");
        assertThat(enriched.getEnglishWord().getFullReadingAudio()).isNotNull();
        assertThat(enriched.getEnglishWord().getFullReadingAudio().getAudioKey())
                .isEqualTo("english_word:full_reading:hello");
        assertThat(enriched.getEnglishWord().getFullReadingAudio().getAudioUrl())
                .isEqualTo("/api/v1/audio/by-key/english_word%3Afull_reading%3Ahello");

        verify(taskService, times(2)).ensureTask(any(AudioGenerationTask.class));
    }

    @Test
    void enrichHeadwordAudioShouldNotAttemptFullReadingWhenHeadwordIsNotReady() {
        AudioStorageService audioStorageService = org.mockito.Mockito.mock(AudioStorageService.class);
        AudioGenerationTaskService taskService = org.mockito.Mockito.mock(AudioGenerationTaskService.class);
        AudioKeyBuilder audioKeyBuilder = new AudioKeyBuilder();
        AudioGenerationExecutor executor = org.mockito.Mockito.mock(AudioGenerationExecutor.class);
        TtsService ttsService = org.mockito.Mockito.mock(TtsService.class);
        EntryAudioService audioService = new EntryAudioService(audioStorageService, audioKeyBuilder, taskService, executor, ttsService);
        EntryQueryResponse response = englishWordResponse("hello");
        response.getEnglishWord().setDefinitions(List.of());
        response.getEnglishWord().setExamples(List.of());

        when(audioStorageService.loadAudioByKey("english_word:headword:hello")).thenReturn(null);
        when(audioStorageService.loadAudioByKey("english_word:full_reading:hello")).thenReturn(null);
        when(ttsService.buildReadingText(eq("hello"), any())).thenReturn("full reading");
        when(executor.execute("english_word:headword:hello")).thenReturn(AudioGenerationExecutor.ExecutionResult.PENDING);

        EntryQueryResponse enriched = audioService.enrichHeadwordAudio(response);

        assertThat(enriched.getEnglishWord().getHeadwordAudio()).isNotNull();
        assertThat(enriched.getEnglishWord().getFullReadingAudio()).isNotNull();
        verify(executor, timeout(1000)).execute("english_word:headword:hello");
        verify(executor, never()).execute("english_word:full_reading:hello");
    }

    @Test
    void enrichHeadwordAudioShouldAttemptFullReadingWhenHeadwordAudioAlreadyExists() {
        AudioStorageService audioStorageService = org.mockito.Mockito.mock(AudioStorageService.class);
        AudioGenerationTaskService taskService = org.mockito.Mockito.mock(AudioGenerationTaskService.class);
        AudioKeyBuilder audioKeyBuilder = new AudioKeyBuilder();
        AudioGenerationExecutor executor = org.mockito.Mockito.mock(AudioGenerationExecutor.class);
        TtsService ttsService = org.mockito.Mockito.mock(TtsService.class);
        EntryAudioService audioService = new EntryAudioService(audioStorageService, audioKeyBuilder, taskService, executor, ttsService);
        EntryQueryResponse response = englishWordResponse("hello");
        response.getEnglishWord().setDefinitions(List.of());
        response.getEnglishWord().setExamples(List.of());

        when(audioStorageService.loadAudioByKey("english_word:headword:hello")).thenReturn(new byte[]{1});
        when(audioStorageService.loadAudioByKey("english_word:full_reading:hello")).thenReturn(null);
        when(audioStorageService.existsByAudioKey("english_word:headword:hello")).thenReturn(true);
        when(ttsService.buildReadingText(eq("hello"), any())).thenReturn("full reading");
        when(executor.execute("english_word:headword:hello")).thenReturn(AudioGenerationExecutor.ExecutionResult.SKIPPED);

        EntryQueryResponse enriched = audioService.enrichHeadwordAudio(response);

        assertThat(enriched.getEnglishWord().getHeadwordAudio()).isNotNull();
        assertThat(enriched.getEnglishWord().getFullReadingAudio()).isNotNull();
        verify(executor, timeout(1000)).execute("english_word:headword:hello");
        verify(executor, timeout(1000)).execute("english_word:full_reading:hello");
    }

    @Test
    void enrichHeadwordAudioShouldRefreshFullReadingTaskWhenSourceTextChanges() {
        AudioStorageService audioStorageService = org.mockito.Mockito.mock(AudioStorageService.class);
        AudioGenerationTaskService taskService = org.mockito.Mockito.mock(AudioGenerationTaskService.class);
        AudioKeyBuilder audioKeyBuilder = new AudioKeyBuilder();
        AudioGenerationExecutor executor = org.mockito.Mockito.mock(AudioGenerationExecutor.class);
        TtsService ttsService = org.mockito.Mockito.mock(TtsService.class);
        EntryAudioService audioService = new EntryAudioService(audioStorageService, audioKeyBuilder, taskService, executor, ttsService);
        EntryQueryResponse response = englishWordResponse("hello");
        response.getEnglishWord().setDefinitions(List.of());
        response.getEnglishWord().setExamples(List.of());

        AudioGenerationTask existingTask = new AudioGenerationTask();
        existingTask.setAudioKey("english_word:full_reading:hello");
        existingTask.setAudioType(AudioType.FULL_READING);
        existingTask.setSourceType(EntryResultType.ENGLISH_WORD);
        existingTask.setSourceText("old reading");
        when(audioStorageService.loadAudioByKey("english_word:headword:hello")).thenReturn(new byte[]{1});
        when(audioStorageService.loadAudioByKey("english_word:full_reading:hello")).thenReturn(new byte[]{2});
        when(audioStorageService.existsByAudioKey("english_word:headword:hello")).thenReturn(true);
        when(taskService.findTask("english_word:full_reading:hello")).thenReturn(java.util.Optional.of(existingTask));
        when(ttsService.buildReadingText(eq("hello"), any())).thenReturn("new reading");
        when(executor.execute("english_word:headword:hello")).thenReturn(AudioGenerationExecutor.ExecutionResult.SKIPPED);

        EntryQueryResponse enriched = audioService.enrichHeadwordAudio(response);

        assertThat(enriched.getEnglishWord().getFullReadingAudio()).isNotNull();
        verify(audioStorageService).deleteAudioByKey("english_word:full_reading:hello");
        verify(taskService).ensureTask(any(AudioGenerationTask.class));
        verify(executor, timeout(1000)).execute("english_word:full_reading:hello");
    }

    @Test
    void enrichHeadwordAudioShouldBootstrapReadyTaskForExistingFullReadingAudio() {
        AudioStorageService audioStorageService = org.mockito.Mockito.mock(AudioStorageService.class);
        AudioGenerationTaskService taskService = org.mockito.Mockito.mock(AudioGenerationTaskService.class);
        AudioKeyBuilder audioKeyBuilder = new AudioKeyBuilder();
        AudioGenerationExecutor executor = org.mockito.Mockito.mock(AudioGenerationExecutor.class);
        TtsService ttsService = org.mockito.Mockito.mock(TtsService.class);
        EntryAudioService audioService = new EntryAudioService(audioStorageService, audioKeyBuilder, taskService, executor, ttsService);
        EntryQueryResponse response = englishWordResponse("hello");
        response.getEnglishWord().setDefinitions(List.of());
        response.getEnglishWord().setExamples(List.of());

        when(audioStorageService.loadAudioByKey("english_word:headword:hello")).thenReturn(new byte[]{1});
        when(audioStorageService.loadAudioByKey("english_word:full_reading:hello")).thenReturn(new byte[]{2});
        when(taskService.findTask("english_word:full_reading:hello")).thenReturn(java.util.Optional.empty());
        when(ttsService.buildReadingText(eq("hello"), any())).thenReturn("full reading");
        when(executor.execute("english_word:headword:hello")).thenReturn(AudioGenerationExecutor.ExecutionResult.SKIPPED);

        EntryQueryResponse enriched = audioService.enrichHeadwordAudio(response);

        assertThat(enriched.getEnglishWord().getFullReadingAudio()).isNotNull();
        verify(taskService).ensureTask(any(AudioGenerationTask.class));
        verify(taskService).markReady("english_word:full_reading:hello");
    }

    private EntryQueryResponse responseOfType(EntryResultType resultType) {
        EntryQueryResponse response = new EntryQueryResponse();
        response.setResultType(resultType);
        return response;
    }

    private EntryQueryResponse englishWordResponse(String word) {
        EntryQueryResponse response = responseOfType(EntryResultType.ENGLISH_WORD);
        EntryQueryResponse.EnglishWordPayload payload = new EntryQueryResponse.EnglishWordPayload();
        payload.setWord(word);
        response.setEnglishWord(payload);
        return response;
    }

    private EntryQueryResponse zhToEnResponse(String term) {
        EntryQueryResponse response = responseOfType(EntryResultType.ZH_TO_EN_TERM);
        EntryQueryResponse.ZhToEnTermPayload payload = new EntryQueryResponse.ZhToEnTermPayload();
        EntryQueryResponse.TermCandidate candidate = new EntryQueryResponse.TermCandidate();
        candidate.setTerm(term);
        payload.setCandidates(List.of(candidate));
        response.setZhToEnTerm(payload);
        return response;
    }

    private String normalizeForStorage(String rawQuery) {
        if (rawQuery == null) {
            return null;
        }
        String normalized = rawQuery.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }
}

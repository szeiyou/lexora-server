package com.songzy.dicserver.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songzy.dicserver.dto.QueryHistoryDetailDTO;
import com.songzy.dicserver.dto.QueryHistorySummaryDTO;
import com.songzy.dicserver.model.AppUser;
import com.songzy.dicserver.model.EntryQueryResponse;
import com.songzy.dicserver.model.EntryResultType;
import com.songzy.dicserver.model.QueryHistoryEvent;
import com.songzy.dicserver.model.QueryResponseSchema;
import com.songzy.dicserver.model.TextTranslationResponse;
import com.songzy.dicserver.repository.AppUserRepository;
import com.songzy.dicserver.repository.QueryHistoryEventRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryHistoryServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private QueryHistoryEventRepository queryHistoryEventRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private QueryTextNormalizer queryTextNormalizer;

    @Mock
    private QueryHistorySummaryBuilder queryHistorySummaryBuilder;

    @Mock
    private TextTranslationKeyBuilder textTranslationKeyBuilder;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private EnglishWordSnapshotService englishWordSnapshotService;

    @InjectMocks
    private QueryHistoryService queryHistoryService;

    private AppUser userReference;

    @BeforeEach
    void setUp() {
        userReference = new AppUser();
        userReference.setId(USER_ID);
        userReference.setUsername("alice");
        lenient().when(appUserRepository.getReferenceById(USER_ID)).thenReturn(userReference);
    }

    @Test
    void recordSuccessShouldPersistHistoryEventWithSerializedResponseAndFingerprints() throws Exception {
        EntryQueryResponse response = new EntryQueryResponse();
        response.setResultType(EntryResultType.ENGLISH_WORD);

        when(queryTextNormalizer.normalizeForStorage("visible")).thenReturn("visible");
        when(queryTextNormalizer.buildDedupeKey(EntryResultType.ENGLISH_WORD, "visible"))
                .thenReturn("english_word:visible");
        when(queryTextNormalizer.fingerprintForDedupeKey(EntryResultType.ENGLISH_WORD, "visible"))
                .thenReturn("fp-visible");
        when(queryHistorySummaryBuilder.buildSummary(response)).thenReturn("可见的");
        when(objectMapper.writeValueAsString(response)).thenReturn("{\"resultType\":\"ENGLISH_WORD\"}");

        queryHistoryService.recordSuccess(USER_ID, " visible ", "visible", response);

        ArgumentCaptor<QueryHistoryEvent> eventCaptor = ArgumentCaptor.forClass(QueryHistoryEvent.class);
        verify(queryHistoryEventRepository).save(eventCaptor.capture());
        QueryHistoryEvent saved = eventCaptor.getValue();
        assertThat(saved.getUser()).isSameAs(userReference);
        assertThat(saved.getRawQuery()).isEqualTo(" visible ");
        assertThat(saved.getNormalizedQuery()).isEqualTo("visible");
        assertThat(saved.getDedupeKey()).isEqualTo("english_word:visible");
        assertThat(saved.getDedupeKeyFingerprint()).isEqualTo("fp-visible");
        assertThat(saved.getResultType()).isEqualTo(EntryResultType.ENGLISH_WORD);
        assertThat(saved.getResponseSchema()).isEqualTo(QueryResponseSchema.ENTRY_QUERY_RESPONSE);
        assertThat(saved.getSummary()).isEqualTo("可见的");
        assertThat(saved.getResponseJson()).isEqualTo("{\"resultType\":\"ENGLISH_WORD\"}");
    }

    @Test
    void recordTextTranslationShouldPersistHistoryEventWithTranslationSchemaAndSummary() throws Exception {
        TextTranslationResponse response = new TextTranslationResponse();
        response.setSourceLanguage("zh");
        response.setTargetLanguage("en");
        response.setTranslatedText("Hello there");

        when(textTranslationKeyBuilder.normalizeTextForStorage("你好")).thenReturn("你好");
        when(textTranslationKeyBuilder.buildDedupeKey("你好", "auto", "en"))
                .thenReturn("text_translation:v1:auto-en");
        when(textTranslationKeyBuilder.buildDedupeKeyFingerprint("你好", "auto", "en"))
                .thenReturn("fp-auto-en");
        when(queryHistorySummaryBuilder.buildTextTranslationSummary(response)).thenReturn("Hello there");
        when(objectMapper.writeValueAsString(response)).thenReturn("{\"translatedText\":\"Hello there\"}");

        queryHistoryService.recordTextTranslation(USER_ID, " 你好 ", "你好", "auto", "en", response);

        ArgumentCaptor<QueryHistoryEvent> eventCaptor = ArgumentCaptor.forClass(QueryHistoryEvent.class);
        verify(queryHistoryEventRepository).save(eventCaptor.capture());
        QueryHistoryEvent saved = eventCaptor.getValue();
        assertThat(saved.getUser()).isSameAs(userReference);
        assertThat(saved.getRawQuery()).isEqualTo(" 你好 ");
        assertThat(saved.getNormalizedQuery()).isEqualTo("你好");
        assertThat(saved.getDedupeKey()).isEqualTo("text_translation:v1:auto-en");
        assertThat(saved.getDedupeKeyFingerprint()).isEqualTo("fp-auto-en");
        assertThat(saved.getResultType()).isEqualTo(EntryResultType.TEXT_TRANSLATION);
        assertThat(saved.getResponseSchema()).isEqualTo(QueryResponseSchema.TEXT_TRANSLATION_RESPONSE);
        assertThat(saved.getSummary()).isEqualTo("Hello there");
        assertThat(saved.getResponseJson()).isEqualTo("{\"translatedText\":\"Hello there\"}");
    }

    @Test
    void recordSuccessShouldThrowWhenResponseSerializationFails() throws Exception {
        EntryQueryResponse response = new EntryQueryResponse();
        response.setResultType(EntryResultType.SENTENCE_TRANSLATION);

        when(queryTextNormalizer.normalizeForStorage("hello")).thenReturn("hello");
        when(queryTextNormalizer.buildDedupeKey(EntryResultType.SENTENCE_TRANSLATION, "hello"))
                .thenReturn("sentence_translation:hello");
        when(queryTextNormalizer.fingerprintForDedupeKey(EntryResultType.SENTENCE_TRANSLATION, "hello"))
                .thenReturn("fp-hello");
        when(queryHistorySummaryBuilder.buildSummary(response)).thenReturn("你好");
        when(objectMapper.writeValueAsString(response)).thenThrow(new JsonProcessingException("boom") { });

        assertThatThrownBy(() -> queryHistoryService.recordSuccess(USER_ID, "hello", "hello", response))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to serialize entry query response");
    }

    @Test
    void recordSuccessWithSnapshotShouldUpsertFreshEnglishWordResult() throws Exception {
        EntryQueryResponse response = new EntryQueryResponse();
        response.setResultType(EntryResultType.ENGLISH_WORD);

        when(queryTextNormalizer.normalizeForStorage("visible")).thenReturn("visible");
        when(queryTextNormalizer.buildDedupeKey(EntryResultType.ENGLISH_WORD, "visible"))
                .thenReturn("english_word:visible");
        when(queryTextNormalizer.fingerprintForDedupeKey(EntryResultType.ENGLISH_WORD, "visible"))
                .thenReturn("fp-visible");
        when(queryHistorySummaryBuilder.buildSummary(response)).thenReturn("可见的");
        when(objectMapper.writeValueAsString(response)).thenReturn("{\"resultType\":\"ENGLISH_WORD\"}");

        queryHistoryService.recordSuccessWithSnapshot(
                USER_ID,
                " visible ",
                "visible",
                response,
                true
        );

        verify(englishWordSnapshotService).upsertFromFreshResult(response);
    }

    @Test
    void recordSuccessWithSnapshotShouldBackfillCachedEnglishWordResult() throws Exception {
        EntryQueryResponse response = new EntryQueryResponse();
        response.setResultType(EntryResultType.ENGLISH_WORD);

        when(queryTextNormalizer.normalizeForStorage("visible")).thenReturn("visible");
        when(queryTextNormalizer.buildDedupeKey(EntryResultType.ENGLISH_WORD, "visible"))
                .thenReturn("english_word:visible");
        when(queryTextNormalizer.fingerprintForDedupeKey(EntryResultType.ENGLISH_WORD, "visible"))
                .thenReturn("fp-visible");
        when(queryHistorySummaryBuilder.buildSummary(response)).thenReturn("可见的");
        when(objectMapper.writeValueAsString(response)).thenReturn("{\"resultType\":\"ENGLISH_WORD\"}");

        queryHistoryService.recordSuccessWithSnapshot(
                USER_ID,
                " visible ",
                "visible",
                response,
                false
        );

        verify(englishWordSnapshotService).backfillIfMissing(response);
    }

    @Test
    void recordSuccessWithSnapshotShouldNotWriteSnapshotForNonEnglishTypes() throws Exception {
        EntryQueryResponse response = new EntryQueryResponse();
        response.setResultType(EntryResultType.SENTENCE_TRANSLATION);

        when(queryTextNormalizer.normalizeForStorage("hello")).thenReturn("hello");
        when(queryTextNormalizer.buildDedupeKey(EntryResultType.SENTENCE_TRANSLATION, "hello"))
                .thenReturn("sentence_translation:hello");
        when(queryTextNormalizer.fingerprintForDedupeKey(EntryResultType.SENTENCE_TRANSLATION, "hello"))
                .thenReturn("fp-hello");
        when(queryHistorySummaryBuilder.buildSummary(response)).thenReturn("你好");
        when(objectMapper.writeValueAsString(response)).thenReturn("{\"resultType\":\"SENTENCE_TRANSLATION\"}");

        queryHistoryService.recordSuccessWithSnapshot(
                USER_ID,
                "hello",
                "hello",
                response,
                true
        );

        verify(englishWordSnapshotService, never()).upsertFromFreshResult(response);
        verify(englishWordSnapshotService, never()).backfillIfMissing(response);
    }

    @Test
    void findLatestResponseShouldIgnoreLegacySentenceTranslationHistoryWhenDedupeKeyNoLongerMatches() {
        QueryHistoryEvent event = new QueryHistoryEvent();
        event.setNormalizedQuery("你好");
        event.setResultType(EntryResultType.SENTENCE_TRANSLATION);
        event.setDedupeKey("sentence_translation:你好");

        when(queryTextNormalizer.normalizeForStorage("你好")).thenReturn("你好");
        when(queryHistoryEventRepository.findFirstByUserIdAndNormalizedQueryIgnoreCaseOrderByCreatedAtDescIdDesc(
                USER_ID,
                "你好"
        ))
                .thenReturn(Optional.of(event));
        when(queryTextNormalizer.buildDedupeKey(EntryResultType.SENTENCE_TRANSLATION, "你好"))
                .thenReturn("sentence_translation:v2:你好");

        Optional<EntryQueryResponse> result = queryHistoryService.findLatestResponse(USER_ID, "你好");

        assertThat(result).isEmpty();
        verifyNoInteractions(objectMapper);
    }

    @Test
    void findLatestTextTranslationShouldDeserializePersistedResponseByFingerprint() throws Exception {
        QueryHistoryEvent event = new QueryHistoryEvent();
        event.setId(22L);
        event.setResponseSchema(QueryResponseSchema.TEXT_TRANSLATION_RESPONSE);
        event.setResponseJson("{\"translatedText\":\"Hello there\"}");

        TextTranslationResponse response = new TextTranslationResponse();
        response.setTranslatedText("Hello there");

        when(textTranslationKeyBuilder.normalizeTextForStorage("你好")).thenReturn("你好");
        when(textTranslationKeyBuilder.buildDedupeKeyFingerprint("你好", "zh", "en"))
                .thenReturn("fp-text");
        when(queryHistoryEventRepository.findFirstByUserIdAndDedupeKeyFingerprintOrderByCreatedAtDescIdDesc(
                USER_ID,
                "fp-text"
        ))
                .thenReturn(Optional.of(event));
        when(objectMapper.readValue("{\"translatedText\":\"Hello there\"}", TextTranslationResponse.class))
                .thenReturn(response);

        Optional<TextTranslationResponse> result = queryHistoryService.findLatestTextTranslation(USER_ID, "你好", "zh", "en");

        assertThat(result).containsSame(response);
    }

    @Test
    void getHistoryShouldMapSearchTimeSortToCreatedAt() {
        when(queryHistoryEventRepository.findLatestUniqueEventsByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(Page.empty());

        queryHistoryService.getHistory(USER_ID, PageRequest.of(0, 20, Sort.by(Sort.Order.desc("searchTime"))));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(queryHistoryEventRepository).findLatestUniqueEventsByUserId(eq(USER_ID), pageableCaptor.capture());
        Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
        Sort.Order idOrder = pageableCaptor.getValue().getSort().getOrderFor("id");
        assertThat(idOrder).isNotNull();
        assertThat(idOrder.getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("searchTime")).isNull();
    }

    @Test
    void getHistoryShouldMapQuerySortToRawQueryAndAppendIdTieBreaker() {
        when(queryHistoryEventRepository.findLatestUniqueEventsByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(Page.empty());

        queryHistoryService.getHistory(USER_ID, PageRequest.of(0, 20, Sort.by(Sort.Order.asc("query"))));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(queryHistoryEventRepository).findLatestUniqueEventsByUserId(eq(USER_ID), pageableCaptor.capture());
        Sort.Order queryOrder = pageableCaptor.getValue().getSort().getOrderFor("rawQuery");
        assertThat(queryOrder).isNotNull();
        assertThat(queryOrder.getDirection()).isEqualTo(Sort.Direction.ASC);
        Sort.Order idOrder = pageableCaptor.getValue().getSort().getOrderFor("id");
        assertThat(idOrder).isNotNull();
        assertThat(idOrder.getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("query")).isNull();
    }

    @Test
    void getHistoryShouldMapSearchTimeAscSortToCreatedAtAscAndIdAsc() {
        when(queryHistoryEventRepository.findLatestUniqueEventsByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(Page.empty());

        queryHistoryService.getHistory(USER_ID, PageRequest.of(0, 20, Sort.by(Sort.Order.asc("searchTime"))));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(queryHistoryEventRepository).findLatestUniqueEventsByUserId(eq(USER_ID), pageableCaptor.capture());
        Sort.Order createdAtOrder = pageableCaptor.getValue().getSort().getOrderFor("createdAt");
        assertThat(createdAtOrder).isNotNull();
        assertThat(createdAtOrder.getDirection()).isEqualTo(Sort.Direction.ASC);
        Sort.Order idOrder = pageableCaptor.getValue().getSort().getOrderFor("id");
        assertThat(idOrder).isNotNull();
        assertThat(idOrder.getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("searchTime")).isNull();
    }

    @Test
    void getHistoryShouldMapLatestSearchTimeSortToCreatedAt() {
        when(queryHistoryEventRepository.findLatestUniqueEventsByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(Page.empty());

        queryHistoryService.getHistory(USER_ID, PageRequest.of(0, 20, Sort.by(Sort.Order.desc("latestSearchTime"))));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(queryHistoryEventRepository).findLatestUniqueEventsByUserId(eq(USER_ID), pageableCaptor.capture());
        Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
        Sort.Order idOrder = pageableCaptor.getValue().getSort().getOrderFor("id");
        assertThat(idOrder).isNotNull();
        assertThat(idOrder.getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("latestSearchTime")).isNull();
    }

    @Test
    void getHistoryShouldMapLatestUniqueGroupsToSummaries() {
        QueryHistoryEvent latestVisible = event(
                201L, "visible", "visible", "fp-visible", EntryResultType.ENGLISH_WORD, "可见的",
                LocalDateTime.of(2026, 3, 25, 18, 30)
        );
        QueryHistoryEvent latestSentence = event(
                301L, "how are you", "how are you", "fp-sentence", EntryResultType.SENTENCE_TRANSLATION, "你好吗",
                LocalDateTime.of(2026, 3, 25, 8, 0)
        );
        Page<QueryHistoryEvent> latestPage = new PageImpl<>(
                List.of(latestVisible, latestSentence),
                PageRequest.of(0, 20),
                2
        );

        List<QueryHistoryEvent> groupedEvents = List.of(
                event(301L, "how are you", "how are you", "fp-sentence", EntryResultType.SENTENCE_TRANSLATION, "你好吗",
                        LocalDateTime.of(2026, 3, 25, 8, 0)),
                event(300L, "how are you", "how are you", "fp-sentence", EntryResultType.SENTENCE_TRANSLATION, "你好吗",
                        LocalDateTime.of(2026, 3, 22, 7, 0)),
                event(201L, "visible", "visible", "fp-visible", EntryResultType.ENGLISH_WORD, "可见的",
                        LocalDateTime.of(2026, 3, 25, 18, 30)),
                event(200L, "visible", "visible", "fp-visible", EntryResultType.ENGLISH_WORD, "可见的",
                        LocalDateTime.of(2026, 3, 24, 9, 15)),
                event(199L, "visible", "visible", "fp-visible", EntryResultType.ENGLISH_WORD, "可见的",
                        LocalDateTime.of(2026, 3, 20, 8, 0))
        );

        when(queryHistoryEventRepository.findLatestUniqueEventsByUserId(eq(USER_ID), any(Pageable.class))).thenReturn(latestPage);
        when(queryHistoryEventRepository
                .findSearchTimesByUserIdAndDedupeKeyFingerprintIn(
                        USER_ID,
                        List.of("fp-visible", "fp-sentence")
                ))
                .thenReturn(groupedEvents.stream()
                        .map(event -> projection(event.getDedupeKeyFingerprint(), event.getCreatedAt()))
                        .toList());

        Page<QueryHistorySummaryDTO> result = queryHistoryService.getHistory(USER_ID, PageRequest.of(0, 20));

        assertThat(result.getContent()).containsExactly(
                new QueryHistorySummaryDTO(
                        "fp-visible",
                        "visible",
                        "visible",
                        EntryResultType.ENGLISH_WORD,
                        "可见的",
                        LocalDateTime.of(2026, 3, 25, 18, 30),
                        3,
                        List.of(
                                LocalDateTime.of(2026, 3, 25, 18, 30),
                                LocalDateTime.of(2026, 3, 24, 9, 15),
                                LocalDateTime.of(2026, 3, 20, 8, 0)
                        )
                ),
                new QueryHistorySummaryDTO(
                        "fp-sentence",
                        "how are you",
                        "how are you",
                        EntryResultType.SENTENCE_TRANSLATION,
                        "你好吗",
                        LocalDateTime.of(2026, 3, 25, 8, 0),
                        2,
                        List.of(
                                LocalDateTime.of(2026, 3, 25, 8, 0),
                                LocalDateTime.of(2026, 3, 22, 7, 0)
                        )
                )
        );
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    void getHistoryDetailShouldReturnEntryResponseForFingerprint() throws Exception {
        QueryHistoryEvent latestVisible = event(
                201L, "visible", "visible", "fp-visible", EntryResultType.ENGLISH_WORD, "可见的",
                LocalDateTime.of(2026, 3, 25, 18, 30)
        );
        latestVisible.setResponseSchema(QueryResponseSchema.ENTRY_QUERY_RESPONSE);
        latestVisible.setResponseJson("{\"resultType\":\"ENGLISH_WORD\"}");

        EntryQueryResponse entryResponse = new EntryQueryResponse();
        entryResponse.setResultType(EntryResultType.ENGLISH_WORD);

        when(queryHistoryEventRepository.findFirstByUserIdAndDedupeKeyFingerprintOrderByCreatedAtDescIdDesc(
                USER_ID,
                "fp-visible"
        ))
                .thenReturn(Optional.of(latestVisible));
        when(queryHistoryEventRepository.findSearchTimesByUserIdAndDedupeKeyFingerprintIn(USER_ID, List.of("fp-visible")))
                .thenReturn(List.of(
                        projection("fp-visible", LocalDateTime.of(2026, 3, 25, 18, 30)),
                        projection("fp-visible", LocalDateTime.of(2026, 3, 24, 9, 15))
                ));
        when(objectMapper.readValue("{\"resultType\":\"ENGLISH_WORD\"}", EntryQueryResponse.class))
                .thenReturn(entryResponse);

        Optional<QueryHistoryDetailDTO> result = queryHistoryService.getHistoryDetail(USER_ID, "fp-visible");

        assertThat(result).isPresent();
        assertThat(result.get().historyKey()).isEqualTo("fp-visible");
        assertThat(result.get().resultType()).isEqualTo(EntryResultType.ENGLISH_WORD);
        assertThat(result.get().query()).isEqualTo("visible");
        assertThat(result.get().normalizedQuery()).isEqualTo("visible");
        assertThat(result.get().latestSearchTime()).isEqualTo(LocalDateTime.of(2026, 3, 25, 18, 30));
        assertThat(result.get().searchCount()).isEqualTo(2);
        assertThat(result.get().searchTimes()).containsExactly(
                LocalDateTime.of(2026, 3, 25, 18, 30),
                LocalDateTime.of(2026, 3, 24, 9, 15)
        );
        assertThat(result.get().response()).isSameAs(entryResponse);
        assertThat(result.get().response()).isInstanceOf(EntryQueryResponse.class);
    }

    @Test
    void getHistoryDetailShouldReturnTextTranslationResponseForFingerprint() throws Exception {
        QueryHistoryEvent event = new QueryHistoryEvent();
        event.setId(22L);
        event.setRawQuery("你好");
        event.setNormalizedQuery("你好");
        event.setDedupeKeyFingerprint("fp-text");
        event.setResultType(EntryResultType.TEXT_TRANSLATION);
        event.setResponseSchema(QueryResponseSchema.TEXT_TRANSLATION_RESPONSE);
        event.setResponseJson("{\"translatedText\":\"Hello there\"}");
        event.setCreatedAt(LocalDateTime.of(2026, 3, 25, 19, 0));

        TextTranslationResponse response = new TextTranslationResponse();
        response.setTranslatedText("Hello there");

        when(queryHistoryEventRepository.findFirstByUserIdAndDedupeKeyFingerprintOrderByCreatedAtDescIdDesc(
                USER_ID,
                "fp-text"
        ))
                .thenReturn(Optional.of(event));
        when(queryHistoryEventRepository.findSearchTimesByUserIdAndDedupeKeyFingerprintIn(USER_ID, List.of("fp-text")))
                .thenReturn(List.of(projection("fp-text", LocalDateTime.of(2026, 3, 25, 19, 0))));
        when(objectMapper.readValue("{\"translatedText\":\"Hello there\"}", TextTranslationResponse.class))
                .thenReturn(response);

        Optional<QueryHistoryDetailDTO> result = queryHistoryService.getHistoryDetail(USER_ID, "fp-text");

        assertThat(result).isPresent();
        assertThat(result.get().historyKey()).isEqualTo("fp-text");
        assertThat(result.get().response()).isSameAs(response);
        assertThat(result.get().response()).isInstanceOf(TextTranslationResponse.class);
    }

    @Test
    void getHistoryDetailShouldReturnEmptyWhenFingerprintMissing() {
        when(queryHistoryEventRepository.findFirstByUserIdAndDedupeKeyFingerprintOrderByCreatedAtDescIdDesc(
                USER_ID,
                "missing"
        ))
                .thenReturn(Optional.empty());

        Optional<QueryHistoryDetailDTO> result = queryHistoryService.getHistoryDetail(USER_ID, "missing");

        assertThat(result).isEmpty();
    }

    @Test
    void getHistoryDetailShouldThrowWhenPersistedPayloadIsCorrupt() throws Exception {
        QueryHistoryEvent event = new QueryHistoryEvent();
        event.setId(45L);
        event.setRawQuery("visible");
        event.setNormalizedQuery("visible");
        event.setDedupeKeyFingerprint("fp-bad");
        event.setResultType(EntryResultType.ENGLISH_WORD);
        event.setResponseSchema(QueryResponseSchema.ENTRY_QUERY_RESPONSE);
        event.setResponseJson("{bad-json");
        event.setCreatedAt(LocalDateTime.of(2026, 3, 25, 10, 30));

        when(queryHistoryEventRepository.findFirstByUserIdAndDedupeKeyFingerprintOrderByCreatedAtDescIdDesc(
                USER_ID,
                "fp-bad"
        ))
                .thenReturn(Optional.of(event));
        when(queryHistoryEventRepository.findSearchTimesByUserIdAndDedupeKeyFingerprintIn(USER_ID, List.of("fp-bad")))
                .thenReturn(List.of(projection("fp-bad", LocalDateTime.of(2026, 3, 25, 10, 30))));
        when(objectMapper.readValue("{bad-json", EntryQueryResponse.class))
                .thenThrow(new JsonProcessingException("boom") { });

        assertThatThrownBy(() -> queryHistoryService.getHistoryDetail(USER_ID, "fp-bad"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to deserialize persisted history detail response");
    }

    private QueryHistoryEventRepository.QueryHistorySearchTimeProjection projection(
            String dedupeKeyFingerprint,
            LocalDateTime createdAt
    ) {
        return new QueryHistoryEventRepository.QueryHistorySearchTimeProjection() {
            @Override
            public String getDedupeKeyFingerprint() {
                return dedupeKeyFingerprint;
            }

            @Override
            public LocalDateTime getCreatedAt() {
                return createdAt;
            }
        };
    }

    private QueryHistoryEvent event(Long id,
                                    String rawQuery,
                                    String normalizedQuery,
                                    String dedupeKeyFingerprint,
                                    EntryResultType resultType,
                                    String summary,
                                    LocalDateTime createdAt) {
        QueryHistoryEvent event = new QueryHistoryEvent();
        event.setId(id);
        event.setRawQuery(rawQuery);
        event.setNormalizedQuery(normalizedQuery);
        event.setDedupeKey(resultType.name().toLowerCase() + ":" + normalizedQuery);
        event.setDedupeKeyFingerprint(dedupeKeyFingerprint);
        event.setResultType(resultType);
        event.setResponseSchema(QueryResponseSchema.ENTRY_QUERY_RESPONSE);
        event.setSummary(summary);
        event.setResponseJson("{\"ok\":true}");
        event.setCreatedAt(createdAt);
        return event;
    }
}

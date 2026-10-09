package com.songzy.dicserver.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songzy.dicserver.dto.QueryHistoryDetailDTO;
import com.songzy.dicserver.dto.QueryHistorySummaryDTO;
import com.songzy.dicserver.model.EntryQueryResponse;
import com.songzy.dicserver.model.EntryResultType;
import com.songzy.dicserver.model.QueryHistoryEvent;
import com.songzy.dicserver.model.QueryResponseSchema;
import com.songzy.dicserver.model.TextTranslationResponse;
import com.songzy.dicserver.repository.AppUserRepository;
import com.songzy.dicserver.repository.QueryHistoryEventRepository;
import com.songzy.dicserver.repository.QueryHistoryEventRepository.QueryHistorySearchTimeProjection;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class QueryHistoryService {

    private static final Logger logger = LoggerFactory.getLogger(QueryHistoryService.class);
    private static final Sort HISTORY_DEFAULT_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
    );

    private final QueryHistoryEventRepository queryHistoryEventRepository;
    private final AppUserRepository appUserRepository;
    private final QueryTextNormalizer queryTextNormalizer;
    private final QueryHistorySummaryBuilder queryHistorySummaryBuilder;
    private final TextTranslationKeyBuilder textTranslationKeyBuilder;
    private final ObjectMapper objectMapper;
    private final EnglishWordSnapshotService englishWordSnapshotService;

    public QueryHistoryService(QueryHistoryEventRepository queryHistoryEventRepository,
                               AppUserRepository appUserRepository,
                               QueryTextNormalizer queryTextNormalizer,
                               QueryHistorySummaryBuilder queryHistorySummaryBuilder,
                               TextTranslationKeyBuilder textTranslationKeyBuilder,
                               ObjectMapper objectMapper,
                               EnglishWordSnapshotService englishWordSnapshotService) {
        this.queryHistoryEventRepository = queryHistoryEventRepository;
        this.appUserRepository = appUserRepository;
        this.queryTextNormalizer = queryTextNormalizer;
        this.queryHistorySummaryBuilder = queryHistorySummaryBuilder;
        this.textTranslationKeyBuilder = textTranslationKeyBuilder;
        this.objectMapper = objectMapper;
        this.englishWordSnapshotService = englishWordSnapshotService;
    }

    @Transactional
    public void recordSuccessWithSnapshot(Long userId,
                                          String rawQuery,
                                          String normalizedQuery,
                                          EntryQueryResponse response,
                                          boolean freshResult) {
        recordSuccess(userId, rawQuery, normalizedQuery, response);

        if (response == null || response.getResultType() != EntryResultType.ENGLISH_WORD) {
            return;
        }

        if (freshResult) {
            englishWordSnapshotService.upsertFromFreshResult(response);
        } else {
            englishWordSnapshotService.backfillIfMissing(response);
        }
    }

    public void recordSuccess(Long userId,
                              String rawQuery,
                              String normalizedQuery,
                              EntryQueryResponse response) {
        if (response == null || response.getResultType() == null) {
            return;
        }

        String normalized = queryTextNormalizer.normalizeForStorage(
                normalizedQuery != null ? normalizedQuery : rawQuery
        );
        if (normalized == null) {
            return;
        }

        EntryResultType resultType = response.getResultType();
        String dedupeKey = queryTextNormalizer.buildDedupeKey(resultType, normalized);
        String dedupeKeyFingerprint = queryTextNormalizer.fingerprintForDedupeKey(resultType, normalized);
        if (dedupeKey == null || dedupeKeyFingerprint == null) {
            return;
        }

        QueryHistoryEvent event = new QueryHistoryEvent();
        event.setUser(appUserRepository.getReferenceById(userId));
        event.setRawQuery(rawQuery);
        event.setNormalizedQuery(normalized);
        event.setDedupeKey(dedupeKey);
        event.setDedupeKeyFingerprint(dedupeKeyFingerprint);
        event.setResultType(resultType);
        event.setResponseSchema(QueryResponseSchema.ENTRY_QUERY_RESPONSE);
        event.setSummary(queryHistorySummaryBuilder.buildSummary(response));
        event.setResponseJson(serializeResponse(response));

        queryHistoryEventRepository.save(event);
    }

    public void recordTextTranslation(Long userId,
                                      String rawText,
                                      String normalizedText,
                                      String sourceLanguage,
                                      String targetLanguage,
                                      TextTranslationResponse response) {
        if (response == null) {
            return;
        }

        String normalized = textTranslationKeyBuilder.normalizeTextForStorage(
                normalizedText != null ? normalizedText : rawText
        );
        if (normalized == null) {
            return;
        }

        String dedupeKey = textTranslationKeyBuilder.buildDedupeKey(normalized, sourceLanguage, targetLanguage);
        String dedupeKeyFingerprint = textTranslationKeyBuilder.buildDedupeKeyFingerprint(
                normalized,
                sourceLanguage,
                targetLanguage
        );
        if (dedupeKey == null || dedupeKeyFingerprint == null) {
            return;
        }

        QueryHistoryEvent event = new QueryHistoryEvent();
        event.setUser(appUserRepository.getReferenceById(userId));
        event.setRawQuery(rawText);
        event.setNormalizedQuery(normalized);
        event.setDedupeKey(dedupeKey);
        event.setDedupeKeyFingerprint(dedupeKeyFingerprint);
        event.setResultType(EntryResultType.TEXT_TRANSLATION);
        event.setResponseSchema(QueryResponseSchema.TEXT_TRANSLATION_RESPONSE);
        event.setSummary(queryHistorySummaryBuilder.buildTextTranslationSummary(response));
        event.setResponseJson(serializeTextTranslationResponse(response));

        queryHistoryEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public Page<QueryHistorySummaryDTO> getHistory(Long userId, Pageable pageable) {
        Page<QueryHistoryEvent> latestEventsPage = queryHistoryEventRepository.findLatestUniqueEventsByUserId(
                userId,
                remapHistoryPageable(pageable)
        );
        List<QueryHistoryEvent> latestEvents = latestEventsPage.getContent();
        if (latestEvents.isEmpty()) {
            return latestEventsPage.map(this::toSummaryDto);
        }

        List<String> fingerprints = latestEvents.stream()
                .map(QueryHistoryEvent::getDedupeKeyFingerprint)
                .distinct()
                .toList();
        Map<String, List<QueryHistorySearchTimeProjection>> searchTimesByFingerprint = queryHistoryEventRepository
                .findSearchTimesByUserIdAndDedupeKeyFingerprintIn(userId, fingerprints)
                .stream()
                .collect(Collectors.groupingBy(
                        QueryHistorySearchTimeProjection::getDedupeKeyFingerprint
                ));

        return latestEventsPage.map(event -> toSummaryDto(
                event,
                searchTimesByFingerprint.getOrDefault(event.getDedupeKeyFingerprint(), List.of())
        ));
    }

    @Transactional(readOnly = true)
    public Optional<QueryHistoryDetailDTO> getHistoryDetail(Long userId, String historyKey) {
        if (historyKey == null || historyKey.isBlank()) {
            return Optional.empty();
        }

        return queryHistoryEventRepository
                .findFirstByUserIdAndDedupeKeyFingerprintOrderByCreatedAtDescIdDesc(userId, historyKey)
                .map(event -> {
                    SearchTimeAggregate searchTimeAggregate = toSearchTimeAggregate(
                            event,
                            queryHistoryEventRepository.findSearchTimesByUserIdAndDedupeKeyFingerprintIn(
                                    userId,
                                    List.of(historyKey)
                            )
                    );
                    return new QueryHistoryDetailDTO(
                            event.getDedupeKeyFingerprint(),
                            event.getResultType(),
                            event.getRawQuery(),
                            event.getNormalizedQuery(),
                            searchTimeAggregate.latestSearchTime(),
                            searchTimeAggregate.searchCount(),
                            searchTimeAggregate.searchTimes(),
                            deserializeHistoryDetailPayload(event)
                    );
                });
    }

    @Transactional(readOnly = true)
    public Optional<EntryQueryResponse> findLatestResponse(Long userId,
                                                           String normalizedQuery,
                                                           EntryResultType requestedType) {
        String dedupeKeyFingerprint = queryTextNormalizer.fingerprintForDedupeKey(requestedType, normalizedQuery);
        if (dedupeKeyFingerprint == null) {
            return Optional.empty();
        }

        return queryHistoryEventRepository
                .findFirstByUserIdAndDedupeKeyFingerprintOrderByCreatedAtDescIdDesc(userId, dedupeKeyFingerprint)
                .flatMap(this::deserializeResponseSafely);
    }

    @Transactional(readOnly = true)
    public Optional<EntryQueryResponse> findLatestResponse(Long userId, String normalizedQuery) {
        String storageNormalized = queryTextNormalizer.normalizeForStorage(normalizedQuery);
        if (storageNormalized == null) {
            return Optional.empty();
        }

        return queryHistoryEventRepository
                .findFirstByUserIdAndNormalizedQueryIgnoreCaseOrderByCreatedAtDescIdDesc(userId, storageNormalized)
                .filter(event -> hasCurrentDedupeKey(event, storageNormalized))
                .flatMap(this::deserializeResponseSafely);
    }

    @Transactional(readOnly = true)
    public Optional<TextTranslationResponse> findLatestTextTranslation(Long userId,
                                                                       String normalizedText,
                                                                       String sourceLanguage,
                                                                       String targetLanguage) {
        String storageNormalized = textTranslationKeyBuilder.normalizeTextForStorage(normalizedText);
        if (storageNormalized == null) {
            return Optional.empty();
        }

        String dedupeKeyFingerprint = textTranslationKeyBuilder.buildDedupeKeyFingerprint(
                storageNormalized,
                sourceLanguage,
                targetLanguage
        );
        if (dedupeKeyFingerprint == null) {
            return Optional.empty();
        }

        return queryHistoryEventRepository
                .findFirstByUserIdAndDedupeKeyFingerprintOrderByCreatedAtDescIdDesc(userId, dedupeKeyFingerprint)
                .flatMap(this::deserializeTextTranslationSafely);
    }

    private QueryHistorySummaryDTO toSummaryDto(QueryHistoryEvent event) {
        return toSummaryDto(event, List.of());
    }

    private QueryHistorySummaryDTO toSummaryDto(QueryHistoryEvent event,
                                                List<QueryHistorySearchTimeProjection> searchTimeProjections) {
        SearchTimeAggregate searchTimeAggregate = toSearchTimeAggregate(event, searchTimeProjections);
        return new QueryHistorySummaryDTO(
                event.getDedupeKeyFingerprint(),
                event.getRawQuery(),
                event.getNormalizedQuery(),
                event.getResultType(),
                event.getSummary(),
                searchTimeAggregate.latestSearchTime(),
                searchTimeAggregate.searchCount(),
                searchTimeAggregate.searchTimes()
        );
    }

    private Pageable remapHistoryPageable(Pageable pageable) {
        if (pageable == null) {
            return PageRequest.of(0, 20, HISTORY_DEFAULT_SORT);
        }
        if (pageable.getSort().isUnsorted()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), HISTORY_DEFAULT_SORT);
        }

        Sort mappedSort = Sort.by(pageable.getSort().stream()
                .map(this::remapHistorySortOrder)
                .toList());
        if (mappedSort.getOrderFor("id") == null) {
            Sort.Direction tieBreakerDirection = mappedSort.stream()
                    .findFirst()
                    .map(Sort.Order::getDirection)
                    .orElse(Sort.Direction.DESC);
            mappedSort = mappedSort.and(Sort.by(tieBreakerDirection, "id"));
        }

        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), mappedSort);
    }

    private Sort.Order remapHistorySortOrder(Sort.Order order) {
        return order.withProperty(switch (order.getProperty()) {
            case "query" -> "rawQuery";
            case "searchTime", "latestSearchTime" -> "createdAt";
            default -> order.getProperty();
        });
    }

    private String serializeResponse(EntryQueryResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize entry query response", ex);
        }
    }

    private String serializeTextTranslationResponse(TextTranslationResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize text translation response", ex);
        }
    }

    private boolean hasCurrentDedupeKey(QueryHistoryEvent event, String normalizedQuery) {
        if (event == null || event.getResultType() == null) {
            return false;
        }
        String currentDedupeKey = queryTextNormalizer.buildDedupeKey(event.getResultType(), normalizedQuery);
        return currentDedupeKey != null && currentDedupeKey.equals(event.getDedupeKey());
    }

    private Optional<EntryQueryResponse> deserializeResponseSafely(QueryHistoryEvent event) {
        if (event == null || event.getResponseSchema() != QueryResponseSchema.ENTRY_QUERY_RESPONSE) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(event.getResponseJson(), EntryQueryResponse.class));
        } catch (Exception ex) {
            logger.warn("Failed to deserialize persisted entry response for history id {}: {}",
                    event.getId(), ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<TextTranslationResponse> deserializeTextTranslationSafely(QueryHistoryEvent event) {
        if (event == null || event.getResponseSchema() != QueryResponseSchema.TEXT_TRANSLATION_RESPONSE) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(event.getResponseJson(), TextTranslationResponse.class));
        } catch (Exception ex) {
            logger.warn("Failed to deserialize persisted text translation response for history id {}: {}",
                    event.getId(), ex.getMessage());
            return Optional.empty();
        }
    }

    private Object deserializeHistoryDetailPayload(QueryHistoryEvent event) {
        if (event == null || event.getResponseSchema() == null) {
            throw new IllegalStateException("Failed to deserialize persisted history detail response");
        }
        try {
            return switch (event.getResponseSchema()) {
                case ENTRY_QUERY_RESPONSE -> objectMapper.readValue(event.getResponseJson(), EntryQueryResponse.class);
                case TEXT_TRANSLATION_RESPONSE ->
                        objectMapper.readValue(event.getResponseJson(), TextTranslationResponse.class);
            };
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialize persisted history detail response", ex);
        }
    }

    private SearchTimeAggregate toSearchTimeAggregate(QueryHistoryEvent event,
                                                      List<QueryHistorySearchTimeProjection> searchTimeProjections) {
        List<LocalDateTime> searchTimes = searchTimeProjections == null
                ? List.of()
                : searchTimeProjections.stream()
                .map(QueryHistorySearchTimeProjection::getCreatedAt)
                .toList();
        if (searchTimes.isEmpty()) {
            searchTimes = List.of(event.getCreatedAt());
        }
        return new SearchTimeAggregate(searchTimes.getFirst(), searchTimes.size(), searchTimes);
    }

    private record SearchTimeAggregate(
            LocalDateTime latestSearchTime,
            long searchCount,
            List<LocalDateTime> searchTimes
    ) {
    }
}

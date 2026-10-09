package com.songzy.dicserver.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songzy.dicserver.model.EntryQueryResponse;
import com.songzy.dicserver.model.EntryResultType;
import com.songzy.dicserver.model.QueryResultSnapshot;
import com.songzy.dicserver.model.QueryResponseSchema;
import com.songzy.dicserver.model.TextTranslationResponse;
import com.songzy.dicserver.repository.QueryResultSnapshotRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueryResultSnapshotService {

    private static final Logger logger = LoggerFactory.getLogger(QueryResultSnapshotService.class);
    private static final List<EntryResultType> AUTO_ENTRY_TYPES = List.of(
            EntryResultType.ENGLISH_WORD,
            EntryResultType.ZH_TO_EN_TERM,
            EntryResultType.SENTENCE_TRANSLATION
    );

    private final QueryResultSnapshotRepository queryResultSnapshotRepository;
    private final QueryTextNormalizer queryTextNormalizer;
    private final TextTranslationKeyBuilder textTranslationKeyBuilder;
    private final QueryHistorySummaryBuilder queryHistorySummaryBuilder;
    private final ObjectMapper objectMapper;

    public QueryResultSnapshotService(QueryResultSnapshotRepository queryResultSnapshotRepository,
                                      QueryTextNormalizer queryTextNormalizer,
                                      TextTranslationKeyBuilder textTranslationKeyBuilder,
                                      QueryHistorySummaryBuilder queryHistorySummaryBuilder,
                                      ObjectMapper objectMapper) {
        this.queryResultSnapshotRepository = queryResultSnapshotRepository;
        this.queryTextNormalizer = queryTextNormalizer;
        this.textTranslationKeyBuilder = textTranslationKeyBuilder;
        this.queryHistorySummaryBuilder = queryHistorySummaryBuilder;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Optional<EntryQueryResponse> findEntrySnapshot(String normalizedQuery, EntryResultType requestedType) {
        if (requestedType != null) {
            String fingerprint = queryTextNormalizer.fingerprintForDedupeKey(requestedType, normalizedQuery);
            if (fingerprint == null) {
                return Optional.empty();
            }
            return queryResultSnapshotRepository.findBySnapshotKeyFingerprint(fingerprint)
                    .filter(snapshot -> snapshot.getResponseSchema() == QueryResponseSchema.ENTRY_QUERY_RESPONSE)
                    .flatMap(this::deserializeEntryResponseSafely);
        }

        String storageNormalized = queryTextNormalizer.normalizeForStorage(normalizedQuery);
        if (storageNormalized == null) {
            return Optional.empty();
        }

        List<String> fingerprints = AUTO_ENTRY_TYPES.stream()
                .map(type -> queryTextNormalizer.fingerprintForDedupeKey(type, storageNormalized))
                .filter(Objects::nonNull)
                .toList();
        if (fingerprints.isEmpty()) {
            return Optional.empty();
        }

        return queryResultSnapshotRepository.findAllBySnapshotKeyFingerprintIn(fingerprints).stream()
                .filter(snapshot -> snapshot.getResponseSchema() == QueryResponseSchema.ENTRY_QUERY_RESPONSE)
                .sorted((left, right) -> {
                    int updatedComparison = right.getContentUpdatedAt().compareTo(left.getContentUpdatedAt());
                    if (updatedComparison != 0) {
                        return updatedComparison;
                    }
                    return Long.compare(
                            right.getId() == null ? Long.MIN_VALUE : right.getId(),
                            left.getId() == null ? Long.MIN_VALUE : left.getId()
                    );
                })
                .map(this::deserializeEntryResponseSafely)
                .flatMap(Optional::stream)
                .findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<TextTranslationResponse> findTextTranslationSnapshot(String normalizedText,
                                                                         String sourceLanguage,
                                                                         String targetLanguage) {
        String fingerprint = textTranslationKeyBuilder.buildDedupeKeyFingerprint(
                normalizedText,
                sourceLanguage,
                targetLanguage
        );
        if (fingerprint == null) {
            return Optional.empty();
        }

        return queryResultSnapshotRepository.findBySnapshotKeyFingerprint(fingerprint)
                .filter(snapshot -> snapshot.getResponseSchema() == QueryResponseSchema.TEXT_TRANSLATION_RESPONSE)
                .flatMap(this::deserializeTextTranslationResponseSafely);
    }

    @Transactional
    public void upsertEntrySnapshot(String normalizedQuery, EntryQueryResponse response) {
        if (response == null || response.getResultType() == null || response.getResultType() == EntryResultType.TEXT_TRANSLATION) {
            return;
        }

        String snapshotKey = queryTextNormalizer.buildDedupeKey(response.getResultType(), normalizedQuery);
        String fingerprint = queryTextNormalizer.fingerprintForDedupeKey(response.getResultType(), normalizedQuery);
        if (snapshotKey == null || fingerprint == null) {
            return;
        }

        QueryResultSnapshot snapshot = queryResultSnapshotRepository.findBySnapshotKeyFingerprint(fingerprint)
                .orElseGet(QueryResultSnapshot::new);
        snapshot.setSnapshotKey(snapshotKey);
        snapshot.setSnapshotKeyFingerprint(fingerprint);
        snapshot.setResultType(response.getResultType());
        snapshot.setResponseSchema(QueryResponseSchema.ENTRY_QUERY_RESPONSE);
        snapshot.setSummary(queryHistorySummaryBuilder.buildSummary(response));
        snapshot.setResponseJson(serializeEntryResponse(response));
        snapshot.setContentUpdatedAt(LocalDateTime.now());
        queryResultSnapshotRepository.saveAndFlush(snapshot);
    }

    @Transactional
    public void upsertTextTranslationSnapshot(String normalizedText,
                                              String sourceLanguage,
                                              String targetLanguage,
                                              TextTranslationResponse response) {
        if (response == null) {
            return;
        }

        String snapshotKey = textTranslationKeyBuilder.buildDedupeKey(normalizedText, sourceLanguage, targetLanguage);
        String fingerprint = textTranslationKeyBuilder.buildDedupeKeyFingerprint(normalizedText, sourceLanguage, targetLanguage);
        if (snapshotKey == null || fingerprint == null) {
            return;
        }

        QueryResultSnapshot snapshot = queryResultSnapshotRepository.findBySnapshotKeyFingerprint(fingerprint)
                .orElseGet(QueryResultSnapshot::new);
        snapshot.setSnapshotKey(snapshotKey);
        snapshot.setSnapshotKeyFingerprint(fingerprint);
        snapshot.setResultType(EntryResultType.TEXT_TRANSLATION);
        snapshot.setResponseSchema(QueryResponseSchema.TEXT_TRANSLATION_RESPONSE);
        snapshot.setSummary(queryHistorySummaryBuilder.buildTextTranslationSummary(response));
        snapshot.setResponseJson(serializeTextTranslationResponse(response));
        snapshot.setContentUpdatedAt(LocalDateTime.now());
        queryResultSnapshotRepository.saveAndFlush(snapshot);
    }

    private String serializeEntryResponse(EntryQueryResponse response) {
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

    private Optional<EntryQueryResponse> deserializeEntryResponseSafely(QueryResultSnapshot snapshot) {
        try {
            return Optional.of(objectMapper.readValue(snapshot.getResponseJson(), EntryQueryResponse.class));
        } catch (Exception ex) {
            logger.warn("Failed to deserialize entry snapshot {}: {}", snapshot.getId(), ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<TextTranslationResponse> deserializeTextTranslationResponseSafely(QueryResultSnapshot snapshot) {
        try {
            return Optional.of(objectMapper.readValue(snapshot.getResponseJson(), TextTranslationResponse.class));
        } catch (Exception ex) {
            logger.warn("Failed to deserialize text translation snapshot {}: {}", snapshot.getId(), ex.getMessage());
            return Optional.empty();
        }
    }
}

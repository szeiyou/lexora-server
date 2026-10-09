package com.songzy.dicserver.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songzy.dicserver.model.EntryQueryResponse;
import com.songzy.dicserver.model.EntryResultType;
import com.songzy.dicserver.repository.EnglishWordSnapshotRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnglishWordSnapshotService {

    private final EnglishWordSnapshotRepository englishWordSnapshotRepository;
    private final QueryTextNormalizer queryTextNormalizer;
    private final ObjectMapper objectMapper;

    public EnglishWordSnapshotService(EnglishWordSnapshotRepository englishWordSnapshotRepository,
                                      QueryTextNormalizer queryTextNormalizer,
                                      ObjectMapper objectMapper) {
        this.englishWordSnapshotRepository = englishWordSnapshotRepository;
        this.queryTextNormalizer = queryTextNormalizer;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void upsertFromFreshResult(EntryQueryResponse response) {
        SnapshotData snapshotData = toSnapshotData(response);
        if (snapshotData == null) {
            return;
        }

        englishWordSnapshotRepository.upsertByFingerprint(
                snapshotData.headword(),
                snapshotData.normalizedHeadword(),
                snapshotData.normalizedHeadwordFingerprint(),
                snapshotData.wordDataJson(),
                LocalDateTime.now()
        );
    }

    @Transactional
    public void backfillIfMissing(EntryQueryResponse response) {
        SnapshotData snapshotData = toSnapshotData(response);
        if (snapshotData == null) {
            return;
        }

        englishWordSnapshotRepository.insertIfAbsentByFingerprint(
                snapshotData.headword(),
                snapshotData.normalizedHeadword(),
                snapshotData.normalizedHeadwordFingerprint(),
                snapshotData.wordDataJson(),
                LocalDateTime.now()
        );
    }

    private SnapshotData toSnapshotData(EntryQueryResponse response) {
        if (response == null
                || response.getResultType() != EntryResultType.ENGLISH_WORD
                || response.getEnglishWord() == null) {
            return null;
        }

        String headword = queryTextNormalizer.normalizeForStorage(response.getEnglishWord().getWord());
        if (headword == null) {
            return null;
        }

        String normalizedHeadword = queryTextNormalizer.normalizeForLookup(headword);
        String normalizedHeadwordFingerprint = queryTextNormalizer.fingerprintForLookup(headword);
        if (normalizedHeadword == null || normalizedHeadwordFingerprint == null) {
            return null;
        }

        return new SnapshotData(
                headword,
                normalizedHeadword,
                normalizedHeadwordFingerprint,
                serializeWordPayload(response.getEnglishWord())
        );
    }

    private String serializeWordPayload(EntryQueryResponse.EnglishWordPayload englishWordPayload) {
        try {
            return objectMapper.writeValueAsString(englishWordPayload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize english word payload", ex);
        }
    }

    private record SnapshotData(
            String headword,
            String normalizedHeadword,
            String normalizedHeadwordFingerprint,
            String wordDataJson
    ) {
    }
}

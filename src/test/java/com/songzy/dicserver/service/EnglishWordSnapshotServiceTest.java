package com.songzy.dicserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songzy.dicserver.model.EnglishWordSnapshot;
import com.songzy.dicserver.model.EntryQueryResponse;
import com.songzy.dicserver.model.EntryResultType;
import com.songzy.dicserver.repository.EnglishWordSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class EnglishWordSnapshotServiceTest {

    @Autowired
    private EnglishWordSnapshotRepository englishWordSnapshotRepository;

    private EnglishWordSnapshotService englishWordSnapshotService;
    private QueryTextNormalizer queryTextNormalizer;

    @BeforeEach
    void setUp() {
        queryTextNormalizer = new QueryTextNormalizer();
        englishWordSnapshotService = new EnglishWordSnapshotService(
                englishWordSnapshotRepository,
                queryTextNormalizer,
                new ObjectMapper()
        );
    }

    @Test
    void backfillIfMissingShouldNotAdvanceContentUpdatedAtWhenSnapshotAlreadyExists() {
        String headword = "Visible";
        String normalizedHeadword = queryTextNormalizer.normalizeForLookup(headword);
        String fingerprint = queryTextNormalizer.fingerprintForLookup(headword);
        LocalDateTime fixedUpdatedAt = LocalDateTime.of(2026, 3, 21, 10, 15, 30);

        EnglishWordSnapshot existing = new EnglishWordSnapshot();
        existing.setHeadword(headword);
        existing.setNormalizedHeadword(normalizedHeadword);
        existing.setNormalizedHeadwordFingerprint(fingerprint);
        existing.setWordDataJson("{\"word\":\"Visible\"}");
        existing.setContentUpdatedAt(fixedUpdatedAt);
        englishWordSnapshotRepository.saveAndFlush(existing);

        englishWordSnapshotService.backfillIfMissing(englishWordResponse(" visible "));

        EnglishWordSnapshot after = englishWordSnapshotRepository
                .findByNormalizedHeadwordFingerprint(fingerprint)
                .orElseThrow();
        assertThat(after.getContentUpdatedAt()).isEqualTo(fixedUpdatedAt);
        assertThat(englishWordSnapshotRepository.count()).isEqualTo(1L);
    }

    private EntryQueryResponse englishWordResponse(String word) {
        EntryQueryResponse response = new EntryQueryResponse();
        response.setResultType(EntryResultType.ENGLISH_WORD);
        EntryQueryResponse.EnglishWordPayload payload = new EntryQueryResponse.EnglishWordPayload();
        payload.setWord(word);
        response.setEnglishWord(payload);
        return response;
    }
}

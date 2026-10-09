package com.songzy.dicserver.repository;

import com.songzy.dicserver.model.EnglishWordSnapshot;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class EnglishWordSnapshotRepositoryTest {

    @Autowired
    private EnglishWordSnapshotRepository repository;

    @Test
    void findByNormalizedHeadwordFingerprintShouldReturnSnapshot() {
        repository.saveAndFlush(snapshot("Visible", "visible", "fp-visible"));

        assertThat(repository.findByNormalizedHeadwordFingerprint("fp-visible"))
                .isPresent()
                .get()
                .extracting(EnglishWordSnapshot::getHeadword)
                .isEqualTo("Visible");
    }

    @Test
    void saveAndFlushShouldEnforceUniqueNormalizedHeadwordFingerprint() {
        repository.saveAndFlush(snapshot("Visible", "visible", "fp-visible"));

        assertThatThrownBy(() -> repository.saveAndFlush(snapshot("VISIBLE", "visible-variant", "fp-visible")))
                .isInstanceOfAny(DataIntegrityViolationException.class, PersistenceException.class);
    }

    @Test
    void saveAndFlushShouldAllowSameReadableNormalizedHeadwordWhenFingerprintDiffers() {
        repository.saveAndFlush(snapshot("Cafe", "cafe", "fp-cafe"));
        repository.saveAndFlush(snapshot("Café", "cafe", "fp-café"));

        assertThat(repository.findAll()).hasSize(2);
    }

    @Test
    void findByNormalizedHeadwordFingerprintShouldResolveCorrectRowWhenReadableKeyCollides() {
        repository.saveAndFlush(snapshot("Cafe", "cafe", "fp-cafe"));
        repository.saveAndFlush(snapshot("Café", "cafe", "fp-café"));

        assertThat(repository.findByNormalizedHeadwordFingerprint("fp-café"))
                .isPresent()
                .get()
                .extracting(EnglishWordSnapshot::getHeadword)
                .isEqualTo("Café");
    }

    @Test
    void upsertByFingerprintShouldUpdateExistingSnapshotWithoutUniqueConstraintFailures() {
        LocalDateTime initialUpdatedAt = LocalDateTime.of(2026, 3, 21, 11, 0, 0);
        LocalDateTime refreshedUpdatedAt = initialUpdatedAt.plusMinutes(5);

        repository.upsertByFingerprint("Visible", "visible", "fp-visible", "{\"word\":\"Visible\"}", initialUpdatedAt);
        repository.upsertByFingerprint("VISIBLE", "visible", "fp-visible", "{\"word\":\"VISIBLE\"}", refreshedUpdatedAt);

        assertThat(repository.count()).isEqualTo(1L);
        EnglishWordSnapshot snapshot = repository.findByNormalizedHeadwordFingerprint("fp-visible").orElseThrow();
        assertThat(snapshot.getHeadword()).isEqualTo("VISIBLE");
        assertThat(snapshot.getWordDataJson()).isEqualTo("{\"word\":\"VISIBLE\"}");
        assertThat(snapshot.getContentUpdatedAt()).isEqualTo(refreshedUpdatedAt);
    }

    @Test
    void insertIfAbsentByFingerprintShouldPreserveExistingSnapshotOnCollision() {
        LocalDateTime initialUpdatedAt = LocalDateTime.of(2026, 3, 21, 11, 0, 0);
        LocalDateTime laterUpdatedAt = initialUpdatedAt.plusMinutes(15);

        repository.insertIfAbsentByFingerprint("Visible", "visible", "fp-visible", "{\"word\":\"Visible\"}", initialUpdatedAt);
        repository.insertIfAbsentByFingerprint("VISIBLE", "visible", "fp-visible", "{\"word\":\"VISIBLE\"}", laterUpdatedAt);

        assertThat(repository.count()).isEqualTo(1L);
        EnglishWordSnapshot snapshot = repository.findByNormalizedHeadwordFingerprint("fp-visible").orElseThrow();
        assertThat(snapshot.getHeadword()).isEqualTo("Visible");
        assertThat(snapshot.getWordDataJson()).isEqualTo("{\"word\":\"Visible\"}");
        assertThat(snapshot.getContentUpdatedAt()).isEqualTo(initialUpdatedAt);
    }

    private EnglishWordSnapshot snapshot(String headword, String normalizedHeadword) {
        return snapshot(headword, normalizedHeadword, "fp-" + normalizedHeadword);
    }

    private EnglishWordSnapshot snapshot(String headword, String normalizedHeadword, String normalizedHeadwordFingerprint) {
        EnglishWordSnapshot snapshot = new EnglishWordSnapshot();
        snapshot.setHeadword(headword);
        snapshot.setNormalizedHeadword(normalizedHeadword);
        snapshot.setNormalizedHeadwordFingerprint(normalizedHeadwordFingerprint);
        snapshot.setWordDataJson("{\"headword\":\"" + headword + "\"}");
        snapshot.setContentUpdatedAt(LocalDateTime.now());
        return snapshot;
    }
}

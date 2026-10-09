package com.songzy.dicserver.repository;

import com.songzy.dicserver.model.EntryResultType;
import com.songzy.dicserver.model.QueryResultSnapshot;
import com.songzy.dicserver.model.QueryResponseSchema;
import jakarta.persistence.PersistenceException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class QueryResultSnapshotRepositoryTest {

    @Autowired
    private QueryResultSnapshotRepository repository;

    @Test
    void findBySnapshotKeyFingerprintShouldReturnSnapshot() {
        repository.saveAndFlush(snapshot("english_word:visible", "fp-visible", EntryResultType.ENGLISH_WORD));

        assertThat(repository.findBySnapshotKeyFingerprint("fp-visible"))
                .isPresent()
                .get()
                .extracting(QueryResultSnapshot::getSnapshotKey)
                .isEqualTo("english_word:visible");
    }

    @Test
    void saveAndFlushShouldEnforceUniqueSnapshotKeyFingerprint() {
        repository.saveAndFlush(snapshot("english_word:visible", "fp-visible", EntryResultType.ENGLISH_WORD));

        assertThatThrownBy(() -> repository.saveAndFlush(snapshot(
                "english_word:visible-v2",
                "fp-visible",
                EntryResultType.ENGLISH_WORD
        ))).isInstanceOfAny(DataIntegrityViolationException.class, PersistenceException.class);
    }

    @Test
    void findAllBySnapshotKeyFingerprintInShouldReturnMatchingRows() {
        repository.saveAndFlush(snapshot("english_word:visible", "fp-visible", EntryResultType.ENGLISH_WORD));
        repository.saveAndFlush(snapshot("zh_to_en_term:苹果", "fp-apple", EntryResultType.ZH_TO_EN_TERM));

        assertThat(repository.findAllBySnapshotKeyFingerprintIn(List.of("fp-visible", "fp-apple")))
                .extracting(QueryResultSnapshot::getSnapshotKeyFingerprint)
                .containsExactlyInAnyOrder("fp-visible", "fp-apple");
    }

    private QueryResultSnapshot snapshot(String snapshotKey,
                                         String snapshotKeyFingerprint,
                                         EntryResultType resultType) {
        QueryResultSnapshot snapshot = new QueryResultSnapshot();
        snapshot.setSnapshotKey(snapshotKey);
        snapshot.setSnapshotKeyFingerprint(snapshotKeyFingerprint);
        snapshot.setResultType(resultType);
        snapshot.setResponseSchema(QueryResponseSchema.ENTRY_QUERY_RESPONSE);
        snapshot.setSummary("summary");
        snapshot.setResponseJson("{\"resultType\":\"" + resultType.name() + "\"}");
        snapshot.setContentUpdatedAt(LocalDateTime.now());
        return snapshot;
    }
}

package com.songzy.dicserver.repository;

import com.songzy.dicserver.model.AudioGenerationStatus;
import com.songzy.dicserver.model.AudioGenerationTask;
import com.songzy.dicserver.model.AudioType;
import com.songzy.dicserver.model.EntryResultType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class AudioGenerationTaskRepositoryTest {

    @Autowired
    private AudioGenerationTaskRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void saveAndFlushShouldEnforceUniqueAudioKey() {
        repository.saveAndFlush(task("english_word:headword:hello", AudioGenerationStatus.PENDING, null));

        assertThatThrownBy(() -> repository.saveAndFlush(task("english_word:headword:hello", AudioGenerationStatus.PENDING, null)))
                .isInstanceOfAny(DataIntegrityViolationException.class, PersistenceException.class);
    }

    @Test
    void findTasksReadyForRetryShouldReturnPendingAndRetryableFailedRows() {
        LocalDateTime now = LocalDateTime.now();
        AudioGenerationTask pending = repository.save(task("english_word:headword:pending", AudioGenerationStatus.PENDING, null));
        AudioGenerationTask retryableFailed = repository.save(task("english_word:headword:retry", AudioGenerationStatus.FAILED, now.minusMinutes(1)));
        repository.save(task("english_word:headword:future", AudioGenerationStatus.FAILED, now.plusMinutes(5)));
        repository.save(task("english_word:headword:processing", AudioGenerationStatus.PROCESSING, now.minusMinutes(1)));
        repository.save(task("english_word:headword:ready", AudioGenerationStatus.READY, null));
        repository.flush();

        List<AudioGenerationTask> tasks = repository.findTasksReadyForRetry(now);

        assertThat(tasks)
                .extracting(AudioGenerationTask::getAudioKey)
                .containsExactlyInAnyOrder(pending.getAudioKey(), retryableFailed.getAudioKey());
    }

    @Test
    void claimForProcessingShouldTransitionOnlyOnce() {
        LocalDateTime now = LocalDateTime.now();
        repository.saveAndFlush(task("english_word:headword:hello", AudioGenerationStatus.PENDING, null));

        int firstClaim = repository.claimForProcessing(
                "english_word:headword:hello",
                AudioGenerationStatus.PENDING,
                AudioGenerationStatus.FAILED,
                AudioGenerationStatus.PROCESSING,
                now
        );
        int secondClaim = repository.claimForProcessing(
                "english_word:headword:hello",
                AudioGenerationStatus.PENDING,
                AudioGenerationStatus.FAILED,
                AudioGenerationStatus.PROCESSING,
                now
        );

        entityManager.clear();

        AudioGenerationTask stored = repository.findByAudioKey("english_word:headword:hello").orElseThrow();
        assertThat(firstClaim).isEqualTo(1);
        assertThat(secondClaim).isZero();
        assertThat(stored.getStatus()).isEqualTo(AudioGenerationStatus.PROCESSING);
        assertThat(stored.getAttemptCount()).isEqualTo(1);
        assertThat(stored.getNextRetryAt()).isNull();
    }

    private AudioGenerationTask task(String audioKey, AudioGenerationStatus status, LocalDateTime nextRetryAt) {
        AudioGenerationTask task = new AudioGenerationTask();
        task.setAudioKey(audioKey);
        task.setAudioType(AudioType.HEADWORD);
        task.setSourceType(EntryResultType.ENGLISH_WORD);
        task.setSourceText(audioKey);
        task.setStatus(status);
        task.setAttemptCount(0);
        task.setNextRetryAt(nextRetryAt);
        task.setLastRequestedAt(LocalDateTime.now());
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        return task;
    }
}

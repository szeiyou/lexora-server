package com.songzy.dicserver.service;

import com.songzy.dicserver.model.AudioGenerationStatus;
import com.songzy.dicserver.model.AudioGenerationTask;
import com.songzy.dicserver.model.AudioType;
import com.songzy.dicserver.model.EntryResultType;
import com.songzy.dicserver.repository.AudioGenerationTaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import(AudioGenerationTaskService.class)
@ActiveProfiles("test")
class AudioGenerationTaskServiceTest {

    @Autowired
    private AudioGenerationTaskService taskService;

    @Autowired
    private AudioGenerationTaskRepository repository;

    @Test
    void ensureTaskShouldCreatePendingTaskWhenMissing() {
        AudioGenerationTask created = taskService.ensureTask(task("english_word:headword:hello", "hello"));

        assertThat(created.getId()).isNotNull();
        assertThat(created.getStatus()).isEqualTo(AudioGenerationStatus.PENDING);
        assertThat(created.getAudioType()).isEqualTo(AudioType.HEADWORD);
        assertThat(created.getSourceType()).isEqualTo(EntryResultType.ENGLISH_WORD);
        assertThat(created.getSourceText()).isEqualTo("hello");
        assertThat(created.getAttemptCount()).isZero();
        assertThat(created.getLastRequestedAt()).isNotNull();
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getUpdatedAt()).isNotNull();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void ensureTaskShouldRefreshLastRequestedAtWithoutDuplicatingRows() {
        AudioGenerationTask first = taskService.ensureTask(task("english_word:headword:hello", "hello"));
        LocalDateTime initialLastRequestedAt = first.getLastRequestedAt().minusMinutes(1);
        LocalDateTime initialCreatedAt = first.getCreatedAt();
        first.setLastRequestedAt(initialLastRequestedAt);
        repository.saveAndFlush(first);

        AudioGenerationTask second = taskService.ensureTask(task("english_word:headword:hello", "hello"));

        assertThat(repository.count()).isEqualTo(1);
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getSourceText()).isEqualTo("hello");
        assertThat(second.getCreatedAt()).isEqualTo(initialCreatedAt);
        assertThat(second.getLastRequestedAt()).isAfter(initialLastRequestedAt);
        assertThat(second.getLastRequestedAt()).isEqualTo(repository.findByAudioKey("english_word:headword:hello").orElseThrow().getLastRequestedAt());
        assertThat(second.getUpdatedAt()).isAfterOrEqualTo(second.getLastRequestedAt());
    }

    @Test
    void ensureTaskShouldPersistExactSourceText() {
        String exactSourceText = "Hello,  world!\nLine 2";

        AudioGenerationTask created = taskService.ensureTask(task("english_word:full_reading:hello", exactSourceText));

        assertThat(created.getSourceText()).isEqualTo(exactSourceText);
        assertThat(repository.findByAudioKey("english_word:full_reading:hello"))
                .get()
                .extracting(AudioGenerationTask::getSourceText)
                .isEqualTo(exactSourceText);
    }

    @Test
    void ensureTaskShouldCoalesceDuplicateInsertRaceWhenFlushHitsUniqueConstraint() {
        AudioGenerationTaskRepository mockedRepository = mock(AudioGenerationTaskRepository.class);
        AudioGenerationTaskService service = new AudioGenerationTaskService(mockedRepository);
        AudioGenerationTask requestedTask = task("english_word:headword:hello", "hello");
        AudioGenerationTask existingTask = storedTask("english_word:headword:hello", "hello");

        when(mockedRepository.findByAudioKey("english_word:headword:hello"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingTask));
        when(mockedRepository.saveAndFlush(requestedTask))
                .thenThrow(new DataIntegrityViolationException("duplicate audio key"));
        when(mockedRepository.saveAndFlush(existingTask)).thenReturn(existingTask);

        AudioGenerationTask ensured = service.ensureTask(requestedTask);

        assertThat(ensured).isSameAs(existingTask);
        assertThat(ensured.getLastRequestedAt()).isNotNull();
        verify(mockedRepository).saveAndFlush(requestedTask);
        verify(mockedRepository).saveAndFlush(existingTask);
    }

    @Test
    void ensureTaskShouldRejectExistingTaskWithMismatchedAudioType() {
        taskService.ensureTask(task("english_word:headword:hello", "hello"));

        AudioGenerationTask mismatchedTask = task("english_word:headword:hello", "hello");
        mismatchedTask.setAudioType(AudioType.FULL_READING);

        assertThatThrownBy(() -> taskService.ensureTask(mismatchedTask))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("audioType");
    }

    @Test
    void ensureTaskShouldRejectExistingTaskWithMismatchedSourceType() {
        taskService.ensureTask(task("english_word:headword:hello", "hello"));

        AudioGenerationTask mismatchedTask = task("english_word:headword:hello", "hello");
        mismatchedTask.setSourceType(EntryResultType.ZH_TO_EN_TERM);

        assertThatThrownBy(() -> taskService.ensureTask(mismatchedTask))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sourceType");
    }

    @Test
    void ensureTaskShouldAcceptExistingTaskWithSemanticallyEquivalentSourceText() {
        taskService.ensureTask(task("english_word:headword:hello", "hello"));

        AudioGenerationTask equivalentTask = task("english_word:headword:hello", "  HELLO  ");

        AudioGenerationTask ensured = taskService.ensureTask(equivalentTask);

        assertThat(ensured.getAudioKey()).isEqualTo("english_word:headword:hello");
        assertThat(ensured.getSourceText()).isEqualTo("hello");
    }

    @Test
    void ensureTaskShouldAcceptExistingTaskWithCanonicallyEquivalentUnicodeSourceText() {
        taskService.ensureTask(task("english_word:headword:é", "é"));

        AudioGenerationTask equivalentTask = task("english_word:headword:é", "e\u0301");

        AudioGenerationTask ensured = taskService.ensureTask(equivalentTask);

        assertThat(ensured.getAudioKey()).isEqualTo("english_word:headword:é");
        assertThat(ensured.getSourceText()).isEqualTo("é");
    }

    @Test
    void ensureTaskShouldRefreshFullReadingSourceTextWhenContentChanges() {
        AudioGenerationTask original = task("english_word:full_reading:hello", "hello reading");
        original.setAudioType(AudioType.FULL_READING);
        AudioGenerationTask created = taskService.ensureTask(original);
        created.setStatus(AudioGenerationStatus.READY);
        repository.saveAndFlush(created);

        AudioGenerationTask refreshed = task("english_word:full_reading:hello", "updated reading");
        refreshed.setAudioType(AudioType.FULL_READING);

        AudioGenerationTask ensured = taskService.ensureTask(refreshed);

        assertThat(ensured.getId()).isEqualTo(created.getId());
        assertThat(ensured.getSourceText()).isEqualTo("updated reading");
        assertThat(ensured.getStatus()).isEqualTo(AudioGenerationStatus.PENDING);
        assertThat(ensured.getLastError()).isNull();
        assertThat(ensured.getNextRetryAt()).isNull();
    }

    @Test
    void ensureTaskShouldRejectExistingTaskWithSemanticallyDifferentSourceText() {
        taskService.ensureTask(task("english_word:headword:hello", "hello"));

        AudioGenerationTask mismatchedTask = task("english_word:headword:hello", "hello!");

        assertThatThrownBy(() -> taskService.ensureTask(mismatchedTask))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sourceText");
    }

    @Test
    void claimTaskForProcessingShouldReturnClaimedTaskWhenPendingExists() {
        taskService.ensureTask(task("english_word:headword:hello", "hello"));

        AudioGenerationTask claimed = taskService.claimTaskForProcessing("english_word:headword:hello").orElseThrow();

        assertThat(claimed.getStatus()).isEqualTo(AudioGenerationStatus.PROCESSING);
        assertThat(claimed.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void claimTaskForProcessingShouldReturnEmptyWhenTaskAlreadyClaimed() {
        taskService.ensureTask(task("english_word:headword:hello", "hello"));

        assertThat(taskService.claimTaskForProcessing("english_word:headword:hello")).isPresent();
        assertThat(taskService.claimTaskForProcessing("english_word:headword:hello")).isEmpty();
    }

    @Test
    void isAwaitingAudioShouldReturnTrueForPendingTask() {
        taskService.ensureTask(task("english_word:headword:pending", "hello"));

        assertThat(taskService.isAwaitingAudio("english_word:headword:pending")).isTrue();
    }

    @Test
    void isAwaitingAudioShouldReturnTrueForProcessingTask() {
        taskService.ensureTask(task("english_word:headword:processing", "hello"));
        taskService.claimTaskForProcessing("english_word:headword:processing");

        assertThat(taskService.isAwaitingAudio("english_word:headword:processing")).isTrue();
    }

    @Test
    void isAwaitingAudioShouldReturnTrueForRetryableFailedTask() {
        AudioGenerationTask task = taskService.ensureTask(task("english_word:headword:failed-retryable", "hello"));
        task.setStatus(AudioGenerationStatus.FAILED);
        task.setNextRetryAt(LocalDateTime.now().minusMinutes(1));
        repository.saveAndFlush(task);

        assertThat(taskService.isAwaitingAudio("english_word:headword:failed-retryable")).isTrue();
    }

    @Test
    void isAwaitingAudioShouldReturnFalseForTerminalFailedTask() {
        AudioGenerationTask task = taskService.ensureTask(task("english_word:headword:failed-terminal", "hello"));
        task.setStatus(AudioGenerationStatus.FAILED);
        task.setNextRetryAt(null);
        repository.saveAndFlush(task);

        assertThat(taskService.isAwaitingAudio("english_word:headword:failed-terminal")).isFalse();
    }

    private AudioGenerationTask task(String audioKey, String sourceText) {
        AudioGenerationTask task = new AudioGenerationTask();
        task.setAudioKey(audioKey);
        task.setAudioType(AudioType.HEADWORD);
        task.setSourceType(EntryResultType.ENGLISH_WORD);
        task.setSourceText(sourceText);
        return task;
    }

    private AudioGenerationTask storedTask(String audioKey, String sourceText) {
        AudioGenerationTask task = task(audioKey, sourceText);
        task.setId(1L);
        task.setStatus(AudioGenerationStatus.PENDING);
        task.setAttemptCount(0);
        task.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        task.setUpdatedAt(LocalDateTime.now().minusMinutes(1));
        task.setLastRequestedAt(LocalDateTime.now().minusMinutes(1));
        return task;
    }
}

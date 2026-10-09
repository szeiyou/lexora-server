package com.songzy.dicserver.service;

import com.songzy.dicserver.model.AudioGenerationStatus;
import com.songzy.dicserver.model.AudioGenerationTask;
import com.songzy.dicserver.repository.AudioGenerationTaskRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class AudioGenerationTaskService {

    private final AudioGenerationTaskRepository repository;

    public AudioGenerationTaskService(AudioGenerationTaskRepository repository) {
        this.repository = repository;
    }

    public AudioGenerationTask ensureTask(AudioGenerationTask requestedTask) {
        LocalDateTime now = LocalDateTime.now();
        Optional<AudioGenerationTask> existing = repository.findByAudioKey(requestedTask.getAudioKey());
        if (existing.isPresent()) {
            AudioGenerationTask task = existing.get();
            assertCompatibleTask(task, requestedTask);
            if (!hasEquivalentSourceText(task, requestedTask)) {
                if (task.getAudioType() != com.songzy.dicserver.model.AudioType.FULL_READING) {
                    throw new IllegalStateException("Existing audio generation task has mismatched sourceText for audioKey: " + requestedTask.getAudioKey());
                }
                task.setSourceText(requestedTask.getSourceText());
                task.setStatus(AudioGenerationStatus.PENDING);
                task.setLastError(null);
                task.setNextRetryAt(null);
            }
            task.setLastRequestedAt(now);
            return repository.saveAndFlush(task);
        }

        requestedTask.setStatus(AudioGenerationStatus.PENDING);
        requestedTask.setAttemptCount(0);
        requestedTask.setLastRequestedAt(now);

        try {
            return repository.saveAndFlush(requestedTask);
        } catch (DataIntegrityViolationException ex) {
            AudioGenerationTask task = repository.findByAudioKey(requestedTask.getAudioKey()).orElseThrow(() -> ex);
            assertCompatibleTask(task, requestedTask);
            if (!hasEquivalentSourceText(task, requestedTask)) {
                if (task.getAudioType() != com.songzy.dicserver.model.AudioType.FULL_READING) {
                    throw new IllegalStateException("Existing audio generation task has mismatched sourceText for audioKey: " + requestedTask.getAudioKey());
                }
                task.setSourceText(requestedTask.getSourceText());
                task.setStatus(AudioGenerationStatus.PENDING);
                task.setLastError(null);
                task.setNextRetryAt(null);
            }
            task.setLastRequestedAt(now);
            return repository.saveAndFlush(task);
        }
    }

    @Transactional(readOnly = true)
    public Optional<AudioGenerationTask> findTask(String audioKey) {
        return repository.findByAudioKey(audioKey);
    }

    @Transactional(readOnly = true)
    public List<AudioGenerationTask> findTasksReadyForRetry() {
        return repository.findTasksReadyForRetry(LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public boolean exists(String audioKey) {
        return repository.existsByAudioKey(audioKey);
    }

    @Transactional(readOnly = true)
    public boolean isAwaitingAudio(String audioKey) {
        return repository.findByAudioKey(audioKey)
                .map(task -> task.getStatus() == AudioGenerationStatus.PENDING
                        || task.getStatus() == AudioGenerationStatus.PROCESSING
                        || (task.getStatus() == AudioGenerationStatus.FAILED && task.getNextRetryAt() != null))
                .orElse(false);
    }

    @Transactional
    public Optional<AudioGenerationTask> claimTaskForProcessing(String audioKey) {
        LocalDateTime now = LocalDateTime.now();
        int updated = repository.claimForProcessing(
                audioKey,
                AudioGenerationStatus.PENDING,
                AudioGenerationStatus.FAILED,
                AudioGenerationStatus.PROCESSING,
                now
        );
        if (updated == 0) {
            return Optional.empty();
        }
        return repository.findByAudioKey(audioKey);
    }

    @Transactional
    public void markReady(String audioKey) {
        repository.findByAudioKey(audioKey).ifPresent(task -> {
            task.setStatus(AudioGenerationStatus.READY);
            task.setLastError(null);
            task.setNextRetryAt(null);
            repository.saveAndFlush(task);
        });
    }

    @Transactional
    public void requeuePending(String audioKey) {
        repository.findByAudioKey(audioKey).ifPresent(task -> {
            task.setStatus(AudioGenerationStatus.PENDING);
            task.setLastError(null);
            repository.saveAndFlush(task);
        });
    }

    @Transactional
    public void markFailed(String audioKey, String errorMessage, LocalDateTime nextRetryAt) {
        repository.findByAudioKey(audioKey).ifPresent(task -> {
            task.setStatus(AudioGenerationStatus.FAILED);
            task.setLastError(errorMessage);
            task.setNextRetryAt(nextRetryAt);
            repository.saveAndFlush(task);
        });
    }

    @Transactional(readOnly = true)
    public boolean hasStatus(String audioKey, AudioGenerationStatus status) {
        return repository.findByAudioKey(audioKey)
                .map(AudioGenerationTask::getStatus)
                .filter(status::equals)
                .isPresent();
    }

    private void assertCompatibleTask(AudioGenerationTask existingTask, AudioGenerationTask requestedTask) {
        if (!Objects.equals(existingTask.getAudioType(), requestedTask.getAudioType())) {
            throw new IllegalStateException("Existing audio generation task has mismatched audioType for audioKey: " + requestedTask.getAudioKey());
        }
        if (!Objects.equals(existingTask.getSourceType(), requestedTask.getSourceType())) {
            throw new IllegalStateException("Existing audio generation task has mismatched sourceType for audioKey: " + requestedTask.getAudioKey());
        }
    }

    private boolean hasEquivalentSourceText(AudioGenerationTask existingTask, AudioGenerationTask requestedTask) {
        return Objects.equals(
                AudioKeyBuilder.normalizeText(existingTask.getSourceText()),
                AudioKeyBuilder.normalizeText(requestedTask.getSourceText()));
    }
}

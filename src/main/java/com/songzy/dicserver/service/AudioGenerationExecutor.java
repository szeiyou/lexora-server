package com.songzy.dicserver.service;

import com.songzy.dicserver.model.AudioGenerationStatus;
import com.songzy.dicserver.model.AudioGenerationTask;
import com.songzy.dicserver.model.AudioType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class AudioGenerationExecutor {

    public enum ExecutionResult {
        READY,
        PENDING,
        FAILED,
        SKIPPED
    }

    private final AudioGenerationTaskService audioGenerationTaskService;
    private final AudioStorageService audioStorageService;
    private final TtsService ttsService;

    public AudioGenerationExecutor(AudioGenerationTaskService audioGenerationTaskService,
                                   AudioStorageService audioStorageService,
                                   TtsService ttsService) {
        this.audioGenerationTaskService = audioGenerationTaskService;
        this.audioStorageService = audioStorageService;
        this.ttsService = ttsService;
    }

    public ExecutionResult execute(String audioKey) {
        Optional<AudioGenerationTask> claimed = audioGenerationTaskService.claimTaskForProcessing(audioKey);
        if (claimed.isEmpty()) {
            return ExecutionResult.SKIPPED;
        }

        AudioGenerationTask task = claimed.get();
        if (audioStorageService.existsByAudioKey(task.getAudioKey())) {
            audioGenerationTaskService.markReady(task.getAudioKey());
            return ExecutionResult.READY;
        }

        if (task.getAudioType() == AudioType.FULL_READING && !fullReadingDependencyReady(task.getAudioKey())) {
            audioGenerationTaskService.requeuePending(task.getAudioKey());
            return ExecutionResult.PENDING;
        }

        try {
            byte[] audioData = ttsService.synthesize(task.getSourceText(), task.getAudioType());
            if (audioData == null || audioData.length == 0) {
                audioGenerationTaskService.markFailed(task.getAudioKey(), "TTS returned no audio",
                        LocalDateTime.now().plusMinutes(10));
                return ExecutionResult.FAILED;
            }

            audioStorageService.saveAudioByKey(task.getAudioKey(), audioData);
            audioGenerationTaskService.markReady(task.getAudioKey());
            return ExecutionResult.READY;
        } catch (RuntimeException ex) {
            audioGenerationTaskService.markFailed(task.getAudioKey(), ex.getMessage(),
                    LocalDateTime.now().plusMinutes(10));
            return ExecutionResult.FAILED;
        }
    }

    private boolean fullReadingDependencyReady(String audioKey) {
        String marker = ":full_reading:";
        int markerIndex = audioKey.indexOf(marker);
        if (markerIndex < 0) {
            return true;
        }

        String headwordKey = audioKey.substring(0, markerIndex) + ":headword:" + audioKey.substring(markerIndex + marker.length());
        return audioStorageService.existsByAudioKey(headwordKey)
                || audioGenerationTaskService.hasStatus(headwordKey, AudioGenerationStatus.READY);
    }
}

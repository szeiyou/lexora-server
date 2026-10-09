package com.songzy.dicserver.service;

import com.songzy.dicserver.model.AudioGenerationStatus;
import com.songzy.dicserver.model.AudioGenerationTask;
import com.songzy.dicserver.model.AudioType;
import com.songzy.dicserver.model.EntryResultType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AudioGenerationExecutorTest {

    @Test
    void executeShouldSaveAudioAndMarkReadyOnHeadwordSuccess() {
        AudioGenerationTaskService taskService = mock(AudioGenerationTaskService.class);
        AudioStorageService storageService = mock(AudioStorageService.class);
        TtsService ttsService = mock(TtsService.class);
        AudioGenerationExecutor executor = new AudioGenerationExecutor(taskService, storageService, ttsService);
        AudioGenerationTask task = task("english_word:headword:hello", AudioType.HEADWORD, "hello");

        when(taskService.claimTaskForProcessing(task.getAudioKey())).thenReturn(Optional.of(task));
        when(storageService.existsByAudioKey(task.getAudioKey())).thenReturn(false);
        when(ttsService.synthesize("hello", AudioType.HEADWORD)).thenReturn(new byte[]{1, 2, 3});

        AudioGenerationExecutor.ExecutionResult result = executor.execute(task.getAudioKey());

        assertThat(result).isEqualTo(AudioGenerationExecutor.ExecutionResult.READY);
        verify(ttsService).synthesize("hello", AudioType.HEADWORD);
        verify(storageService).saveAudioByKey(task.getAudioKey(), new byte[]{1, 2, 3});
        verify(taskService).markReady(task.getAudioKey());
    }

    @Test
    void executeShouldMarkFailedWhenSynthesisThrows() {
        AudioGenerationTaskService taskService = mock(AudioGenerationTaskService.class);
        AudioStorageService storageService = mock(AudioStorageService.class);
        TtsService ttsService = mock(TtsService.class);
        AudioGenerationExecutor executor = new AudioGenerationExecutor(taskService, storageService, ttsService);
        AudioGenerationTask task = task("english_word:headword:hello", AudioType.HEADWORD, "hello");

        when(taskService.claimTaskForProcessing(task.getAudioKey())).thenReturn(Optional.of(task));
        when(storageService.existsByAudioKey(task.getAudioKey())).thenReturn(false);
        when(ttsService.synthesize("hello", AudioType.HEADWORD)).thenThrow(new RuntimeException("boom"));

        AudioGenerationExecutor.ExecutionResult result = executor.execute(task.getAudioKey());

        assertThat(result).isEqualTo(AudioGenerationExecutor.ExecutionResult.FAILED);
        verify(taskService).markFailed(eq(task.getAudioKey()), eq("boom"), any(LocalDateTime.class));
    }

    @Test
    void executeShouldPersistProviderFailureMessageWhenSynthesisFails() {
        AudioGenerationTaskService taskService = mock(AudioGenerationTaskService.class);
        AudioStorageService storageService = mock(AudioStorageService.class);
        TtsService ttsService = mock(TtsService.class);
        AudioGenerationExecutor executor = new AudioGenerationExecutor(taskService, storageService, ttsService);
        AudioGenerationTask task = task("english_word:headword:hello", AudioType.HEADWORD, "hello");

        when(taskService.claimTaskForProcessing(task.getAudioKey())).thenReturn(Optional.of(task));
        when(storageService.existsByAudioKey(task.getAudioKey())).thenReturn(false);
        when(ttsService.synthesize("hello", AudioType.HEADWORD))
                .thenThrow(new RuntimeException("Qwen3 TTS接口调用失败: 401 Unauthorized"));

        AudioGenerationExecutor.ExecutionResult result = executor.execute(task.getAudioKey());

        assertThat(result).isEqualTo(AudioGenerationExecutor.ExecutionResult.FAILED);
        verify(taskService).markFailed(eq(task.getAudioKey()),
                eq("Qwen3 TTS接口调用失败: 401 Unauthorized"), any(LocalDateTime.class));
    }

    @Test
    void executeShouldRequeueFullReadingWhenDependencyIsNotReady() {
        AudioGenerationTaskService taskService = mock(AudioGenerationTaskService.class);
        AudioStorageService storageService = mock(AudioStorageService.class);
        TtsService ttsService = mock(TtsService.class);
        AudioGenerationExecutor executor = new AudioGenerationExecutor(taskService, storageService, ttsService);
        AudioGenerationTask task = task("english_word:full_reading:hello", AudioType.FULL_READING, "hello reading");

        when(taskService.claimTaskForProcessing(task.getAudioKey())).thenReturn(Optional.of(task));
        when(storageService.existsByAudioKey(task.getAudioKey())).thenReturn(false);
        when(storageService.existsByAudioKey("english_word:headword:hello")).thenReturn(false);
        when(taskService.hasStatus("english_word:headword:hello", AudioGenerationStatus.READY)).thenReturn(false);

        AudioGenerationExecutor.ExecutionResult result = executor.execute(task.getAudioKey());

        assertThat(result).isEqualTo(AudioGenerationExecutor.ExecutionResult.PENDING);
        verify(taskService).requeuePending(task.getAudioKey());
        verify(ttsService, never()).synthesize(any(), any());
    }

    @Test
    void executeShouldPassFullReadingTypeToTtsServiceWhenDependencyIsReady() {
        AudioGenerationTaskService taskService = mock(AudioGenerationTaskService.class);
        AudioStorageService storageService = mock(AudioStorageService.class);
        TtsService ttsService = mock(TtsService.class);
        AudioGenerationExecutor executor = new AudioGenerationExecutor(taskService, storageService, ttsService);
        AudioGenerationTask task = task("english_word:full_reading:hello", AudioType.FULL_READING, "hello reading");

        when(taskService.claimTaskForProcessing(task.getAudioKey())).thenReturn(Optional.of(task));
        when(storageService.existsByAudioKey(task.getAudioKey())).thenReturn(false);
        when(storageService.existsByAudioKey("english_word:headword:hello")).thenReturn(true);
        when(ttsService.synthesize("hello reading", AudioType.FULL_READING)).thenReturn(new byte[]{4, 5});

        AudioGenerationExecutor.ExecutionResult result = executor.execute(task.getAudioKey());

        assertThat(result).isEqualTo(AudioGenerationExecutor.ExecutionResult.READY);
        verify(ttsService).synthesize("hello reading", AudioType.FULL_READING);
        verify(storageService).saveAudioByKey(task.getAudioKey(), new byte[]{4, 5});
        verify(taskService).markReady(task.getAudioKey());
    }

    @Test
    void executeShouldSkipWhenTaskCannotBeClaimed() {
        AudioGenerationTaskService taskService = mock(AudioGenerationTaskService.class);
        AudioStorageService storageService = mock(AudioStorageService.class);
        TtsService ttsService = mock(TtsService.class);
        AudioGenerationExecutor executor = new AudioGenerationExecutor(taskService, storageService, ttsService);

        when(taskService.claimTaskForProcessing("english_word:headword:hello")).thenReturn(Optional.empty());

        AudioGenerationExecutor.ExecutionResult result = executor.execute("english_word:headword:hello");

        assertThat(result).isEqualTo(AudioGenerationExecutor.ExecutionResult.SKIPPED);
        verify(storageService, never()).existsByAudioKey(any());
        verify(ttsService, never()).synthesize(any(), any());
    }

    private AudioGenerationTask task(String audioKey, AudioType audioType, String sourceText) {
        AudioGenerationTask task = new AudioGenerationTask();
        task.setAudioKey(audioKey);
        task.setAudioType(audioType);
        task.setSourceType(EntryResultType.ENGLISH_WORD);
        task.setSourceText(sourceText);
        return task;
    }
}

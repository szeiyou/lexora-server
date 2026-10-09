package com.songzy.dicserver.service;

import com.songzy.dicserver.model.AudioGenerationTask;
import com.songzy.dicserver.model.AudioType;
import com.songzy.dicserver.model.EntryResultType;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AudioGenerationSchedulerTest {

    @Test
    void processNightlyWindowShouldLoadModelsAndExecuteReadyTasksUpToBatchSizeWithinSameDayWindow() {
        AudioGenerationTaskService taskService = mock(AudioGenerationTaskService.class);
        AudioGenerationExecutor executor = mock(AudioGenerationExecutor.class);
        TtsService ttsService = mock(TtsService.class);
        AudioGenerationScheduler scheduler = schedulerAt(taskService, executor, ttsService,
                "00:00", "02:00", "Asia/Shanghai", ZonedDateTime.of(2026, 3, 22, 1, 0, 0, 0,
                        ZoneId.of("Asia/Shanghai")));
        ReflectionTestUtils.setField(scheduler, "batchSize", 2);

        when(ttsService.loadActiveProviderModels()).thenReturn(true);
        when(taskService.findTasksReadyForRetry()).thenReturn(List.of(
                task("english_word:headword:one"),
                task("english_word:headword:two"),
                task("english_word:headword:three")
        ));

        scheduler.processNightlyWindow();

        verify(ttsService).loadActiveProviderModels();
        verify(taskService).findTasksReadyForRetry();
        verify(executor).execute("english_word:headword:one");
        verify(executor).execute("english_word:headword:two");
        verify(executor, never()).execute("english_word:headword:three");
    }

    @Test
    void processNightlyWindowShouldSkipExecutionOutsideSameDayWindow() {
        AudioGenerationTaskService taskService = mock(AudioGenerationTaskService.class);
        AudioGenerationExecutor executor = mock(AudioGenerationExecutor.class);
        TtsService ttsService = mock(TtsService.class);
        AudioGenerationScheduler scheduler = schedulerAt(taskService, executor, ttsService,
                "00:00", "02:00", "Asia/Shanghai", ZonedDateTime.of(2026, 3, 22, 3, 0, 0, 0,
                        ZoneId.of("Asia/Shanghai")));

        scheduler.processNightlyWindow();

        verify(ttsService, never()).loadActiveProviderModels();
        verify(taskService, never()).findTasksReadyForRetry();
        verify(executor, never()).execute(anyString());
    }

    @Test
    void processNightlyWindowShouldLoadModelsAndExecuteTasksWithinCrossMidnightWindow() {
        AudioGenerationTaskService taskService = mock(AudioGenerationTaskService.class);
        AudioGenerationExecutor executor = mock(AudioGenerationExecutor.class);
        TtsService ttsService = mock(TtsService.class);
        AudioGenerationScheduler scheduler = schedulerAt(taskService, executor, ttsService,
                "23:00", "01:00", "Asia/Shanghai", ZonedDateTime.of(2026, 3, 23, 0, 30, 0, 0,
                        ZoneId.of("Asia/Shanghai")));

        when(ttsService.loadActiveProviderModels()).thenReturn(true);
        when(taskService.findTasksReadyForRetry()).thenReturn(List.of(task("english_word:headword:one")));

        scheduler.processNightlyWindow();

        verify(ttsService).loadActiveProviderModels();
        verify(taskService).findTasksReadyForRetry();
        verify(executor).execute("english_word:headword:one");
    }

    @Test
    void processNightlyWindowShouldSkipWhenModelsCannotBeLoadedInsideWindow() {
        AudioGenerationTaskService taskService = mock(AudioGenerationTaskService.class);
        AudioGenerationExecutor executor = mock(AudioGenerationExecutor.class);
        TtsService ttsService = mock(TtsService.class);
        AudioGenerationScheduler scheduler = schedulerAt(taskService, executor, ttsService,
                "00:00", "02:00", "Asia/Shanghai", ZonedDateTime.of(2026, 3, 22, 1, 0, 0, 0,
                        ZoneId.of("Asia/Shanghai")));

        when(ttsService.loadActiveProviderModels()).thenReturn(false);

        scheduler.processNightlyWindow();

        verify(ttsService).loadActiveProviderModels();
        verify(taskService, never()).findTasksReadyForRetry();
        verify(executor, never()).execute(anyString());
    }

    @Test
    void processNightlyWindowShouldUnloadOnceAfterLeavingWindow() {
        AudioGenerationTaskService taskService = mock(AudioGenerationTaskService.class);
        AudioGenerationExecutor executor = mock(AudioGenerationExecutor.class);
        TtsService ttsService = mock(TtsService.class);
        AudioGenerationScheduler scheduler = schedulerAt(taskService, executor, ttsService,
                "00:00", "02:00", "Asia/Shanghai", ZonedDateTime.of(2026, 3, 22, 1, 0, 0, 0,
                        ZoneId.of("Asia/Shanghai")));

        when(ttsService.loadActiveProviderModels()).thenReturn(true);
        when(ttsService.unloadActiveProviderModels()).thenReturn(true);
        when(taskService.findTasksReadyForRetry()).thenReturn(List.of());

        scheduler.processNightlyWindow();
        ReflectionTestUtils.setField(scheduler, "clock", fixedClock(ZonedDateTime.of(
                2026, 3, 22, 3, 0, 0, 0, ZoneId.of("Asia/Shanghai"))));
        scheduler.processNightlyWindow();
        scheduler.processNightlyWindow();

        verify(ttsService).loadActiveProviderModels();
        verify(ttsService, times(1)).unloadActiveProviderModels();
    }

    @Test
    void processNightlyWindowShouldLoadOnlyOnceAcrossMultipleTicksWithinWindow() {
        AudioGenerationTaskService taskService = mock(AudioGenerationTaskService.class);
        AudioGenerationExecutor executor = mock(AudioGenerationExecutor.class);
        TtsService ttsService = mock(TtsService.class);
        AudioGenerationScheduler scheduler = schedulerAt(taskService, executor, ttsService,
                "00:00", "02:00", "Asia/Shanghai", ZonedDateTime.of(2026, 3, 22, 1, 0, 0, 0,
                        ZoneId.of("Asia/Shanghai")));

        when(ttsService.loadActiveProviderModels()).thenReturn(true);
        when(taskService.findTasksReadyForRetry()).thenReturn(List.of());

        scheduler.processNightlyWindow();
        scheduler.processNightlyWindow();

        verify(ttsService, times(1)).loadActiveProviderModels();
        verify(taskService, times(2)).findTasksReadyForRetry();
    }

    @Test
    void processNightlyWindowShouldSkipInvalidWindowWhereStartEqualsEnd() {
        AudioGenerationTaskService taskService = mock(AudioGenerationTaskService.class);
        AudioGenerationExecutor executor = mock(AudioGenerationExecutor.class);
        TtsService ttsService = mock(TtsService.class);
        AudioGenerationScheduler scheduler = schedulerAt(taskService, executor, ttsService,
                "00:00", "00:00", "Asia/Shanghai", ZonedDateTime.of(2026, 3, 22, 0, 30, 0, 0,
                        ZoneId.of("Asia/Shanghai")));

        scheduler.processNightlyWindow();

        verify(ttsService, never()).loadActiveProviderModels();
        verify(taskService, never()).findTasksReadyForRetry();
        verify(executor, never()).execute(anyString());
    }

    @Test
    void processNightlyWindowShouldSkipWhenZoneIdIsInvalid() {
        AudioGenerationTaskService taskService = mock(AudioGenerationTaskService.class);
        AudioGenerationExecutor executor = mock(AudioGenerationExecutor.class);
        TtsService ttsService = mock(TtsService.class);
        AudioGenerationScheduler scheduler = schedulerAt(taskService, executor, ttsService,
                "00:00", "02:00", "Mars/Nowhere", ZonedDateTime.of(2026, 3, 22, 0, 30, 0, 0,
                        ZoneId.of("Asia/Shanghai")));

        scheduler.processNightlyWindow();

        verify(ttsService, never()).loadActiveProviderModels();
        verify(taskService, never()).findTasksReadyForRetry();
        verify(executor, never()).execute(anyString());
    }

    private AudioGenerationScheduler schedulerAt(AudioGenerationTaskService taskService,
                                                 AudioGenerationExecutor executor,
                                                 TtsService ttsService,
                                                 String windowStart,
                                                 String windowEnd,
                                                 String zoneId,
                                                 ZonedDateTime now) {
        AudioGenerationScheduler scheduler = new AudioGenerationScheduler(taskService, executor, ttsService);
        ReflectionTestUtils.setField(scheduler, "batchSize", 50);
        ReflectionTestUtils.setField(scheduler, "windowStart", windowStart);
        ReflectionTestUtils.setField(scheduler, "windowEnd", windowEnd);
        ReflectionTestUtils.setField(scheduler, "zoneId", zoneId);
        ReflectionTestUtils.setField(scheduler, "clock", fixedClock(now));
        return scheduler;
    }

    private Clock fixedClock(ZonedDateTime now) {
        return Clock.fixed(now.toInstant(), ZoneOffset.UTC);
    }

    private AudioGenerationTask task(String audioKey) {
        AudioGenerationTask task = new AudioGenerationTask();
        task.setAudioKey(audioKey);
        task.setAudioType(AudioType.HEADWORD);
        task.setSourceType(EntryResultType.ENGLISH_WORD);
        task.setSourceText("hello");
        return task;
    }
}

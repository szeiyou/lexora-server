package com.songzy.dicserver.service;

import com.songzy.dicserver.model.AudioGenerationTask;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AudioGenerationScheduler {
    private static final Logger logger = LoggerFactory.getLogger(AudioGenerationScheduler.class);

    private final AudioGenerationTaskService audioGenerationTaskService;
    private final AudioGenerationExecutor audioGenerationExecutor;
    private final TtsService ttsService;

    @Value("${audio.generation.scheduler.batch-size:50}")
    private int batchSize;

    @Value("${audio.generation.scheduler.window.start:00:00}")
    private String windowStart;

    @Value("${audio.generation.scheduler.window.end:02:00}")
    private String windowEnd;

    @Value("${audio.generation.scheduler.zone-id:Asia/Shanghai}")
    private String zoneId;

    private Clock clock = Clock.systemUTC();
    private volatile boolean windowLoaded;

    public AudioGenerationScheduler(AudioGenerationTaskService audioGenerationTaskService,
                                    AudioGenerationExecutor audioGenerationExecutor,
                                    TtsService ttsService) {
        this.audioGenerationTaskService = audioGenerationTaskService;
        this.audioGenerationExecutor = audioGenerationExecutor;
        this.ttsService = ttsService;
    }

    @Scheduled(fixedDelayString = "${audio.generation.scheduler.tick-seconds:300}", timeUnit = TimeUnit.SECONDS)
    public void processNightlyWindow() {
        LocalTime start;
        LocalTime end;
        try {
            start = LocalTime.parse(windowStart);
            end = LocalTime.parse(windowEnd);
        } catch (DateTimeParseException ex) {
            logger.warn("Skip nightly audio generation because scheduler window could not be parsed: start={} end={}",
                    windowStart, windowEnd);
            return;
        }

        if (start.equals(end)) {
            logger.warn("Skip nightly audio generation because scheduler window is invalid: start={} end={}",
                    windowStart, windowEnd);
            return;
        }

        LocalTime now;
        try {
            now = currentLocalTime();
        } catch (DateTimeException ex) {
            logger.warn("Skip nightly audio generation because scheduler zone-id is invalid: {}", zoneId);
            return;
        }

        if (!isWithinWindow(now, start, end)) {
            unloadIfNeeded();
            return;
        }

        if (!windowLoaded) {
            if (!ttsService.loadActiveProviderModels()) {
                logger.warn("Skip nightly audio generation because provider models could not be loaded");
                return;
            }
            windowLoaded = true;
        }

        processReadyTasks();
    }

    boolean isWithinWindow(LocalTime now, LocalTime start, LocalTime end) {
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        return !now.isBefore(start) || now.isBefore(end);
    }

    LocalTime currentLocalTime() {
        return currentZonedDateTime().toLocalTime();
    }

    ZonedDateTime currentZonedDateTime() {
        return ZonedDateTime.now(clock.withZone(ZoneId.of(zoneId)));
    }

    private void processReadyTasks() {
        List<AudioGenerationTask> tasks = audioGenerationTaskService.findTasksReadyForRetry();
        if (tasks.isEmpty()) {
            logger.debug("No audio generation tasks ready for nightly processing");
            return;
        }

        tasks.stream()
                .limit(Math.max(batchSize, 1))
                .forEach(task -> audioGenerationExecutor.execute(task.getAudioKey()));
    }

    private void unloadIfNeeded() {
        if (!windowLoaded) {
            return;
        }
        if (!ttsService.unloadActiveProviderModels()) {
            logger.warn("Failed to unload provider models after nightly audio generation window");
        }
        windowLoaded = false;
    }
}

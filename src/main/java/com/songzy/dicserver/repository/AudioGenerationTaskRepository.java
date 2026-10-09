package com.songzy.dicserver.repository;

import com.songzy.dicserver.model.AudioGenerationStatus;
import com.songzy.dicserver.model.AudioGenerationTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AudioGenerationTaskRepository extends JpaRepository<AudioGenerationTask, Long> {

    Optional<AudioGenerationTask> findByAudioKey(String audioKey);

    boolean existsByAudioKey(String audioKey);

    @Query("""
            select task
            from AudioGenerationTask task
            where task.status = :pendingStatus
               or (task.status = :failedStatus and task.nextRetryAt is not null and task.nextRetryAt <= :now)
            order by task.lastRequestedAt asc
            """)
    List<AudioGenerationTask> findTasksReadyForRetry(
            @Param("now") LocalDateTime now,
            @Param("pendingStatus") AudioGenerationStatus pendingStatus,
            @Param("failedStatus") AudioGenerationStatus failedStatus
    );

    default List<AudioGenerationTask> findTasksReadyForRetry(LocalDateTime now) {
        return findTasksReadyForRetry(now, AudioGenerationStatus.PENDING, AudioGenerationStatus.FAILED);
    }

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AudioGenerationTask task
            set task.status = :processingStatus,
                task.attemptCount = task.attemptCount + 1,
                task.nextRetryAt = null,
                task.updatedAt = :claimedAt
            where task.audioKey = :audioKey
              and (task.status = :pendingStatus
                   or (task.status = :failedStatus and task.nextRetryAt is not null and task.nextRetryAt <= :claimedAt))
            """)
    int claimForProcessing(
            @Param("audioKey") String audioKey,
            @Param("pendingStatus") AudioGenerationStatus pendingStatus,
            @Param("failedStatus") AudioGenerationStatus failedStatus,
            @Param("processingStatus") AudioGenerationStatus processingStatus,
            @Param("claimedAt") LocalDateTime claimedAt
    );
}

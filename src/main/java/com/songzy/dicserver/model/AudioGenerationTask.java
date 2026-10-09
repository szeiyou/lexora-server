package com.songzy.dicserver.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "audio_generation_task",
        indexes = {
                @Index(name = "idx_audio_generation_task_status_retry", columnList = "status,next_retry_at")
        }
)
public class AudioGenerationTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "audio_key", nullable = false, unique = true, length = 512)
    private String audioKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "audio_type", nullable = false, length = 32)
    private AudioType audioType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private EntryResultType sourceType;

    @Column(name = "source_text", nullable = false, columnDefinition = "TEXT")
    private String sourceText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AudioGenerationStatus status;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "last_requested_at", nullable = false)
    private LocalDateTime lastRequestedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (status == null) {
            status = AudioGenerationStatus.PENDING;
        }
        if (attemptCount == null) {
            attemptCount = 0;
        }
        if (lastRequestedAt == null) {
            lastRequestedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAudioKey() {
        return audioKey;
    }

    public void setAudioKey(String audioKey) {
        this.audioKey = audioKey;
    }

    public AudioType getAudioType() {
        return audioType;
    }

    public void setAudioType(AudioType audioType) {
        this.audioType = audioType;
    }

    public EntryResultType getSourceType() {
        return sourceType;
    }

    public void setSourceType(EntryResultType sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceText() {
        return sourceText;
    }

    public void setSourceText(String sourceText) {
        this.sourceText = sourceText;
    }

    public AudioGenerationStatus getStatus() {
        return status;
    }

    public void setStatus(AudioGenerationStatus status) {
        this.status = status;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public LocalDateTime getLastRequestedAt() {
        return lastRequestedAt;
    }

    public void setLastRequestedAt(LocalDateTime lastRequestedAt) {
        this.lastRequestedAt = lastRequestedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

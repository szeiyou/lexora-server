package com.songzy.dicserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.FetchType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Entity
@Table(
        name = "query_history_event",
        indexes = {
                @Index(name = "idx_query_history_event_user_created_at", columnList = "user_id,created_at"),
                @Index(name = "idx_query_history_event_user_dedupe_created_at", columnList = "user_id,dedupe_key,created_at"),
                @Index(name = "idx_query_history_event_user_dedupe_fp_created_at", columnList = "user_id,dedupe_key_fingerprint,created_at")
        }
)
public class QueryHistoryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "raw_query", nullable = false, columnDefinition = "TEXT")
    private String rawQuery;

    @Column(name = "normalized_query", nullable = false, columnDefinition = "TEXT")
    private String normalizedQuery;

    @Column(name = "dedupe_key", nullable = false, length = 512)
    private String dedupeKey;

    @Column(name = "dedupe_key_fingerprint", nullable = false, length = 64)
    private String dedupeKeyFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_type", nullable = false, length = 64)
    private EntryResultType resultType;

    @Enumerated(EnumType.STRING)
    @Column(name = "response_schema", nullable = false, length = 64)
    private QueryResponseSchema responseSchema;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "response_json", nullable = false, columnDefinition = "TEXT")
    private String responseJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

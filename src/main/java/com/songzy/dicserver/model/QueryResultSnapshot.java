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
import lombok.Data;

@Data
@Entity
@Table(
        name = "query_result_snapshot",
        indexes = @Index(
                name = "idx_query_result_snapshot_fp",
                columnList = "snapshot_key_fingerprint",
                unique = true
        )
)
public class QueryResultSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_key", nullable = false, length = 512)
    private String snapshotKey;

    @Column(name = "snapshot_key_fingerprint", nullable = false, length = 64, unique = true)
    private String snapshotKeyFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_type", nullable = false, length = 64)
    private EntryResultType resultType;

    @Enumerated(EnumType.STRING)
    @Column(name = "response_schema", nullable = false, length = 64)
    private QueryResponseSchema responseSchema;

    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "response_json", nullable = false, columnDefinition = "TEXT")
    private String responseJson;

    @Column(name = "content_updated_at", nullable = false)
    private LocalDateTime contentUpdatedAt;

    @PrePersist
    @PreUpdate
    void ensureTimestamps() {
        if (contentUpdatedAt == null) {
            contentUpdatedAt = LocalDateTime.now();
        }
    }
}

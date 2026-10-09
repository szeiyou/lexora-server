package com.songzy.dicserver.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "english_word_snapshot",
        indexes = {
                @Index(name = "idx_english_word_snapshot_normalized_headword", columnList = "normalized_headword"),
                @Index(name = "idx_english_word_snapshot_normalized_headword_fp", columnList = "normalized_headword_fingerprint", unique = true)
        }
)
public class EnglishWordSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String headword;

    @Column(name = "normalized_headword", nullable = false, length = 255)
    private String normalizedHeadword;

    @Column(name = "normalized_headword_fingerprint", nullable = false, length = 64, unique = true)
    private String normalizedHeadwordFingerprint;

    @Column(name = "word_data_json", nullable = false, columnDefinition = "TEXT")
    private String wordDataJson;

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

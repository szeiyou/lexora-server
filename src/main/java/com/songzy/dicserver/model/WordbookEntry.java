package com.songzy.dicserver.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "wordbook_entries", uniqueConstraints = @UniqueConstraint(columnNames = {"wordbook_id", "normalized_word"}))
public class WordbookEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wordbook_id", nullable = false)
    private Wordbook wordbook;

    @Column(nullable = false)
    private String word;

    @Column(name = "normalized_word", nullable = false)
    private String normalizedWord;

    @Column(nullable = false)
    private String partOfSpeech;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String meaning;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}

package com.songzy.dicserver.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class EnglishWordSnapshotWriteRepositoryImpl implements EnglishWordSnapshotWriteRepository {

    private static final String MYSQL_UPSERT_SQL = """
            insert into english_word_snapshot
            (headword, normalized_headword, normalized_headword_fingerprint, word_data_json, content_updated_at)
            values
            (:headword, :normalizedHeadword, :normalizedHeadwordFingerprint, :wordDataJson, :contentUpdatedAt)
            on duplicate key update
              headword = :headword,
              normalized_headword = :normalizedHeadword,
              word_data_json = :wordDataJson,
              content_updated_at = :contentUpdatedAt
            """;

    private static final String H2_UPSERT_SQL = """
            merge into english_word_snapshot
            (headword, normalized_headword, normalized_headword_fingerprint, word_data_json, content_updated_at)
            key(normalized_headword_fingerprint)
            values
            (:headword, :normalizedHeadword, :normalizedHeadwordFingerprint, :wordDataJson, :contentUpdatedAt)
            """;

    private static final String MYSQL_INSERT_IF_ABSENT_SQL = """
            insert into english_word_snapshot
            (headword, normalized_headword, normalized_headword_fingerprint, word_data_json, content_updated_at)
            values
            (:headword, :normalizedHeadword, :normalizedHeadwordFingerprint, :wordDataJson, :contentUpdatedAt)
            on duplicate key update
              normalized_headword_fingerprint = normalized_headword_fingerprint
            """;

    private static final String H2_INSERT_IF_ABSENT_SQL = """
            merge into english_word_snapshot t
            using (
                values (:headword, :normalizedHeadword, :normalizedHeadwordFingerprint, :wordDataJson, :contentUpdatedAt)
            ) s(headword, normalized_headword, normalized_headword_fingerprint, word_data_json, content_updated_at)
            on t.normalized_headword_fingerprint = s.normalized_headword_fingerprint
            when not matched then
              insert (headword, normalized_headword, normalized_headword_fingerprint, word_data_json, content_updated_at)
              values (s.headword, s.normalized_headword, s.normalized_headword_fingerprint, s.word_data_json, s.content_updated_at)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public EnglishWordSnapshotWriteRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Override
    public int upsertByFingerprint(String headword,
                                   String normalizedHeadword,
                                   String normalizedHeadwordFingerprint,
                                   String wordDataJson,
                                   LocalDateTime contentUpdatedAt) {
        return jdbcTemplate.update(
                isMySql() ? MYSQL_UPSERT_SQL : H2_UPSERT_SQL,
                buildParams(headword, normalizedHeadword, normalizedHeadwordFingerprint, wordDataJson, contentUpdatedAt)
        );
    }

    @Override
    public int insertIfAbsentByFingerprint(String headword,
                                           String normalizedHeadword,
                                           String normalizedHeadwordFingerprint,
                                           String wordDataJson,
                                           LocalDateTime contentUpdatedAt) {
        return jdbcTemplate.update(
                isMySql() ? MYSQL_INSERT_IF_ABSENT_SQL : H2_INSERT_IF_ABSENT_SQL,
                buildParams(headword, normalizedHeadword, normalizedHeadwordFingerprint, wordDataJson, contentUpdatedAt)
        );
    }

    private MapSqlParameterSource buildParams(String headword,
                                              String normalizedHeadword,
                                              String normalizedHeadwordFingerprint,
                                              String wordDataJson,
                                              LocalDateTime contentUpdatedAt) {
        return new MapSqlParameterSource()
                .addValue("headword", headword)
                .addValue("normalizedHeadword", normalizedHeadword)
                .addValue("normalizedHeadwordFingerprint", normalizedHeadwordFingerprint)
                .addValue("wordDataJson", wordDataJson)
                .addValue("contentUpdatedAt", contentUpdatedAt);
    }

    private boolean isMySql() {
        try (Connection connection = dataSource.getConnection()) {
            String databaseName = connection.getMetaData().getDatabaseProductName();
            return databaseName != null && databaseName.toLowerCase().contains("mysql");
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to resolve database product name", ex);
        }
    }
}

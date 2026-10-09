package com.songzy.dicserver.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class EnglishWordSnapshotWriteRepositoryImplTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private DatabaseMetaData databaseMetaData;

    private EnglishWordSnapshotWriteRepositoryImpl repository;

    @BeforeEach
    void setUp() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(jdbcTemplate.update(any(String.class), any(MapSqlParameterSource.class))).thenReturn(1);
        repository = new EnglishWordSnapshotWriteRepositoryImpl(jdbcTemplate, dataSource);
    }

    @Test
    void insertIfAbsentByFingerprintShouldUseDuplicateSafeNoopSqlForMySql() throws Exception {
        when(databaseMetaData.getDatabaseProductName()).thenReturn("MySQL");

        repository.insertIfAbsentByFingerprint(
                "Visible",
                "visible",
                "fp-visible",
                "{\"word\":\"Visible\"}",
                LocalDateTime.of(2026, 3, 21, 12, 0, 0)
        );

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), any(MapSqlParameterSource.class));
        String sql = sqlCaptor.getValue().toLowerCase();
        assertThat(sql).contains("on duplicate key update");
        assertThat(sql).doesNotContain("insert ignore");
    }

    @Test
    void insertIfAbsentByFingerprintShouldUseMergeSqlForH2() throws Exception {
        when(databaseMetaData.getDatabaseProductName()).thenReturn("H2");

        repository.insertIfAbsentByFingerprint(
                "Visible",
                "visible",
                "fp-visible",
                "{\"word\":\"Visible\"}",
                LocalDateTime.of(2026, 3, 21, 12, 0, 0)
        );

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), any(MapSqlParameterSource.class));
        String sql = sqlCaptor.getValue().toLowerCase();
        assertThat(sql).contains("merge into english_word_snapshot");
        assertThat(sql).contains("when not matched then");
    }
}

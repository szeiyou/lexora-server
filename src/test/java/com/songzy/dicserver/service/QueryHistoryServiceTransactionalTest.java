package com.songzy.dicserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songzy.dicserver.model.AppUser;
import com.songzy.dicserver.model.EntryQueryResponse;
import com.songzy.dicserver.model.EntryResultType;
import com.songzy.dicserver.model.UserStatus;
import com.songzy.dicserver.repository.AppUserRepository;
import com.songzy.dicserver.repository.QueryHistoryEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

@DataJpaTest
@ActiveProfiles("test")
@Import({
        QueryHistoryService.class,
        QueryHistorySummaryBuilder.class,
        QueryTextNormalizer.class,
        TextTranslationKeyBuilder.class,
        QueryHistoryServiceTransactionalTest.TestBeans.class
})
class QueryHistoryServiceTransactionalTest {

    private Long userId;

    @TestConfiguration
    static class TestBeans {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    private QueryHistoryService queryHistoryService;

    @Autowired
    private QueryHistoryEventRepository queryHistoryEventRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @MockitoBean
    private EnglishWordSnapshotService englishWordSnapshotService;

    @BeforeEach
    void setUp() {
        AppUser user = new AppUser();
        user.setUsername("alice");
        user.setPasswordHash("hashed-password");
        user.setStatus(UserStatus.ACTIVE);
        userId = appUserRepository.saveAndFlush(user).getId();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void recordSuccessWithSnapshotShouldRollbackHistoryWhenSnapshotWriteFails() {
        EntryQueryResponse response = new EntryQueryResponse();
        response.setResultType(EntryResultType.ENGLISH_WORD);
        EntryQueryResponse.EnglishWordPayload englishWordPayload = new EntryQueryResponse.EnglishWordPayload();
        englishWordPayload.setWord("visible");
        response.setEnglishWord(englishWordPayload);

        doThrow(new IllegalStateException("snapshot write failed"))
                .when(englishWordSnapshotService)
                .upsertFromFreshResult(response);

        assertThatThrownBy(() -> queryHistoryService.recordSuccessWithSnapshot(
                userId,
                "visible",
                "visible",
                response,
                true
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("snapshot write failed");

        assertThat(queryHistoryEventRepository.count()).isZero();
    }
}

package com.songzy.dicserver.repository;

import com.songzy.dicserver.model.AppUser;
import com.songzy.dicserver.model.EntryResultType;
import com.songzy.dicserver.model.QueryHistoryEvent;
import com.songzy.dicserver.model.QueryResponseSchema;
import com.songzy.dicserver.model.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class QueryHistoryEventRepositoryTest {

    @Autowired
    private QueryHistoryEventRepository repository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Test
    void deleteByCreatedAtBeforeShouldDeleteOnlyRowsOlderThanCutoff() {
        LocalDateTime now = LocalDateTime.now();
        AppUser user = appUserRepository.saveAndFlush(user("alice"));
        repository.saveAndFlush(event(user, "old query", now.minusDays(10)));
        repository.saveAndFlush(event(user, "recent query", now.minusHours(1)));

        int deleted = repository.deleteByCreatedAtBefore(now.minusDays(1));

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findAll())
                .extracting(QueryHistoryEvent::getRawQuery)
                .containsExactly("recent query");
    }

    @Test
    void findLatestUniqueEventsShouldReturnNewestEventPerDedupeKeyInReverseChronologicalOrder() {
        LocalDateTime base = LocalDateTime.of(2026, 3, 21, 12, 0);
        AppUser user = appUserRepository.saveAndFlush(user("alice"));
        repository.saveAndFlush(event(user, "old visible", "english_word:visible", "fp-visible", base.minusMinutes(30)));
        repository.saveAndFlush(event(user, "new visible", "english_word:visible", "fp-visible", base.minusMinutes(5)));
        repository.saveAndFlush(event(user, "single apple", "english_word:apple", "fp-apple", base.minusMinutes(10)));
        repository.saveAndFlush(event(user, "old sentence", "sentence_translation:how are you", "fp-sentence", base.minusMinutes(20)));
        repository.saveAndFlush(event(user, "new sentence", "sentence_translation:how are you", "fp-sentence", base.minusMinutes(1)));

        Page<QueryHistoryEvent> latestUniqueEvents = repository.findLatestUniqueEventsByUserId(user.getId(), PageRequest.of(
                0,
                2,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        ));

        assertThat(latestUniqueEvents.getContent())
                .extracting(QueryHistoryEvent::getRawQuery)
                .containsExactly("new sentence", "new visible");
        assertThat(latestUniqueEvents.getTotalElements()).isEqualTo(3);
    }

    @Test
    void findLatestUniqueEventsShouldGroupByFingerprintNotReadableDedupeKey() {
        LocalDateTime base = LocalDateTime.of(2026, 3, 21, 12, 0);
        AppUser user = appUserRepository.saveAndFlush(user("alice"));
        repository.saveAndFlush(event(user, "cafe plain", "english_word:cafe", "fp-cafe", base.minusMinutes(5)));
        repository.saveAndFlush(event(user, "cafe accent", "english_word:cafe", "fp-café", base.minusMinutes(1)));

        Page<QueryHistoryEvent> latestUniqueEvents = repository.findLatestUniqueEventsByUserId(user.getId(), PageRequest.of(
                0,
                10,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        ));

        assertThat(latestUniqueEvents.getContent())
                .extracting(QueryHistoryEvent::getRawQuery)
                .containsExactly("cafe accent", "cafe plain");
    }

    @Test
    void findLatestUniqueEventsShouldHonorAscendingCreatedAtSort() {
        LocalDateTime base = LocalDateTime.of(2026, 3, 21, 12, 0);
        AppUser user = appUserRepository.saveAndFlush(user("alice"));
        repository.saveAndFlush(event(user, "old visible", "english_word:visible", "fp-visible", base.minusMinutes(30)));
        repository.saveAndFlush(event(user, "new visible", "english_word:visible", "fp-visible", base.minusMinutes(5)));
        repository.saveAndFlush(event(user, "single apple", "english_word:apple", "fp-apple", base.minusMinutes(10)));
        repository.saveAndFlush(event(user, "old sentence", "sentence_translation:how are you", "fp-sentence", base.minusMinutes(20)));
        repository.saveAndFlush(event(user, "new sentence", "sentence_translation:how are you", "fp-sentence", base.minusMinutes(1)));

        Page<QueryHistoryEvent> latestUniqueEvents = repository.findLatestUniqueEventsByUserId(user.getId(), PageRequest.of(
                0,
                3,
                Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))
        ));

        assertThat(latestUniqueEvents.getContent())
                .extracting(QueryHistoryEvent::getRawQuery)
                .containsExactly("single apple", "new visible", "new sentence");
    }

    @Test
    void findSearchTimesByDedupeKeyFingerprintInShouldBatchLoadGroupedSearchTimes() {
        LocalDateTime base = LocalDateTime.of(2026, 3, 25, 18, 30);
        AppUser user = appUserRepository.saveAndFlush(user("alice"));
        repository.saveAndFlush(event(user, "old visible", "english_word:visible", "fp-visible", base.minusDays(1)));
        repository.saveAndFlush(event(user, "new visible", "english_word:visible", "fp-visible", base));
        repository.saveAndFlush(event(user, "old sentence", "sentence_translation:how are you", "fp-sentence", base.minusDays(2)));
        repository.saveAndFlush(event(user, "new sentence", "sentence_translation:how are you", "fp-sentence", base.minusHours(2)));

        List<QueryHistoryEventRepository.QueryHistorySearchTimeProjection> groupedEvents = repository
                .findSearchTimesByUserIdAndDedupeKeyFingerprintIn(
                        user.getId(),
                        List.of("fp-visible", "fp-sentence")
                );

        assertThat(groupedEvents).hasSize(4);
        assertThat(groupedEvents)
                .extracting(
                        QueryHistoryEventRepository.QueryHistorySearchTimeProjection::getDedupeKeyFingerprint,
                        QueryHistoryEventRepository.QueryHistorySearchTimeProjection::getCreatedAt
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("fp-sentence", base.minusHours(2)),
                        org.assertj.core.groups.Tuple.tuple("fp-sentence", base.minusDays(2)),
                        org.assertj.core.groups.Tuple.tuple("fp-visible", base),
                        org.assertj.core.groups.Tuple.tuple("fp-visible", base.minusDays(1))
                );
    }

    private QueryHistoryEvent event(AppUser user, String rawQuery, LocalDateTime createdAt) {
        return event(
                user,
                rawQuery,
                "english_word:" + rawQuery.replace(" ", "_"),
                "fp-" + rawQuery.replace(" ", "_"),
                createdAt
        );
    }

    private QueryHistoryEvent event(AppUser user, String rawQuery, String dedupeKey, LocalDateTime createdAt) {
        return event(user, rawQuery, dedupeKey, "fp-" + dedupeKey, createdAt);
    }

    private QueryHistoryEvent event(AppUser user,
                                    String rawQuery,
                                    String dedupeKey,
                                    String dedupeKeyFingerprint,
                                    LocalDateTime createdAt) {
        QueryHistoryEvent event = new QueryHistoryEvent();
        event.setUser(user);
        event.setRawQuery(rawQuery);
        event.setNormalizedQuery(rawQuery);
        event.setDedupeKey(dedupeKey);
        event.setDedupeKeyFingerprint(dedupeKeyFingerprint);
        event.setResultType(EntryResultType.ENGLISH_WORD);
        event.setResponseSchema(QueryResponseSchema.ENTRY_QUERY_RESPONSE);
        event.setSummary("summary");
        event.setResponseJson("{\"ok\":true}");
        event.setCreatedAt(createdAt);
        return event;
    }

    private AppUser user(String username) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash("encoded-password");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}

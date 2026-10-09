package com.songzy.dicserver.repository;

import com.songzy.dicserver.model.QueryHistoryEvent;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface QueryHistoryEventRepository extends JpaRepository<QueryHistoryEvent, Long> {

    interface QueryHistorySearchTimeProjection {
        String getDedupeKeyFingerprint();

        LocalDateTime getCreatedAt();
    }

    @Transactional
    int deleteByCreatedAtBefore(LocalDateTime cutoff);

    Optional<QueryHistoryEvent> findFirstByUserIdAndDedupeKeyFingerprintOrderByCreatedAtDescIdDesc(
            Long userId,
            String dedupeKeyFingerprint
    );

    Optional<QueryHistoryEvent> findFirstByUserIdAndNormalizedQueryIgnoreCaseOrderByCreatedAtDescIdDesc(
            Long userId,
            String normalizedQuery
    );

    @Query(value = """
            select event
            from QueryHistoryEvent event
            where event.user.id = :userId
              and not exists (
                select 1
                from QueryHistoryEvent newer
                where newer.user.id = :userId
                  and newer.dedupeKeyFingerprint = event.dedupeKeyFingerprint
                  and (
                    newer.createdAt > event.createdAt
                    or (newer.createdAt = event.createdAt and newer.id > event.id)
                  )
            )
            """,
            countQuery = """
                    select count(distinct event.dedupeKeyFingerprint)
                    from QueryHistoryEvent event
                    where event.user.id = :userId
                    """)
    Page<QueryHistoryEvent> findLatestUniqueEventsByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            select event.dedupeKeyFingerprint as dedupeKeyFingerprint, event.createdAt as createdAt
            from QueryHistoryEvent event
            where event.user.id = :userId
              and event.dedupeKeyFingerprint in :fingerprints
            order by event.dedupeKeyFingerprint asc, event.createdAt desc, event.id desc
            """)
    List<QueryHistorySearchTimeProjection> findSearchTimesByUserIdAndDedupeKeyFingerprintIn(
            @Param("userId") Long userId,
            @Param("fingerprints") Collection<String> fingerprints
    );

}

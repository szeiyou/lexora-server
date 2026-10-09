package com.songzy.dicserver.repository;

import com.songzy.dicserver.model.EnglishWordSnapshot;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnglishWordSnapshotRepository
        extends JpaRepository<EnglishWordSnapshot, Long>, EnglishWordSnapshotWriteRepository {

    Optional<EnglishWordSnapshot> findByNormalizedHeadwordFingerprint(String normalizedHeadwordFingerprint);
}

package com.songzy.dicserver.repository;

import com.songzy.dicserver.model.QueryResultSnapshot;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueryResultSnapshotRepository extends JpaRepository<QueryResultSnapshot, Long> {

    Optional<QueryResultSnapshot> findBySnapshotKeyFingerprint(String snapshotKeyFingerprint);

    List<QueryResultSnapshot> findAllBySnapshotKeyFingerprintIn(Collection<String> snapshotKeyFingerprints);
}

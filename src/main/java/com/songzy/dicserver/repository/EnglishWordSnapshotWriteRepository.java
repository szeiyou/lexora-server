package com.songzy.dicserver.repository;

import java.time.LocalDateTime;

public interface EnglishWordSnapshotWriteRepository {

    int upsertByFingerprint(String headword,
                           String normalizedHeadword,
                           String normalizedHeadwordFingerprint,
                           String wordDataJson,
                           LocalDateTime contentUpdatedAt);

    int insertIfAbsentByFingerprint(String headword,
                                    String normalizedHeadword,
                                    String normalizedHeadwordFingerprint,
                                    String wordDataJson,
                                    LocalDateTime contentUpdatedAt);
}

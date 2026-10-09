package com.songzy.dicserver.repository;

import com.songzy.dicserver.model.WordbookEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WordbookEntryRepository extends JpaRepository<WordbookEntry, Long> {

    @Query("""
            select entry
            from WordbookEntry entry
            where entry.wordbook.id = :wordbookId and entry.wordbook.user.id = :userId
            """)
    List<WordbookEntry> findByWordbookIdAndUserId(@Param("wordbookId") Long wordbookId,
                                                  @Param("userId") Long userId);

    @Modifying
    @Query("""
            delete from WordbookEntry entry
            where entry.wordbook.id = :wordbookId
              and entry.wordbook.user.id = :userId
              and entry.normalizedWord in :normalizedWords
            """)
    void deleteByWordbookIdAndUserIdAndNormalizedWordIn(@Param("wordbookId") Long wordbookId,
                                                        @Param("userId") Long userId,
                                                        @Param("normalizedWords") List<String> normalizedWords);

    @Modifying
    @Query("""
            delete from WordbookEntry entry
            where entry.wordbook.id = :wordbookId and entry.wordbook.user.id = :userId
            """)
    void deleteByWordbookIdAndUserId(@Param("wordbookId") Long wordbookId,
                                     @Param("userId") Long userId);

    @Query("""
            select entry.normalizedWord
            from WordbookEntry entry
            where entry.wordbook.id = :wordbookId and entry.wordbook.user.id = :userId
            """)
    List<String> findNormalizedWordsByWordbookIdAndUserId(@Param("wordbookId") Long wordbookId,
                                                          @Param("userId") Long userId);

    @Query("""
            select entry.wordbook.id
            from WordbookEntry entry
            where entry.normalizedWord = :normalizedWord and entry.wordbook.user.id = :userId
            """)
    List<Long> findWordbookIdsByNormalizedWordAndUserId(@Param("normalizedWord") String normalizedWord,
                                                        @Param("userId") Long userId);
}

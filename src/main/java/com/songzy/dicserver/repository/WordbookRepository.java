package com.songzy.dicserver.repository;

import com.songzy.dicserver.model.Wordbook;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WordbookRepository extends JpaRepository<Wordbook, Long> {

    List<Wordbook> findAllByUserIdOrderByCreateTimeDesc(Long userId);

    Optional<Wordbook> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndName(Long userId, String name);

    boolean existsByIdAndUserId(Long id, Long userId);
}

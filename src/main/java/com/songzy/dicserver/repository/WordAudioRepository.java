package com.songzy.dicserver.repository;

import com.songzy.dicserver.model.WordAudio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface WordAudioRepository extends JpaRepository<WordAudio, Long> {
    Optional<WordAudio> findByWord(String word);

    @Transactional
    void deleteByWord(String word);
}

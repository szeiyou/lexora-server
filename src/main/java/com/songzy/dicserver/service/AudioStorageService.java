package com.songzy.dicserver.service;

import com.songzy.dicserver.model.WordAudio;
import com.songzy.dicserver.repository.WordAudioRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class AudioStorageService {
    private static final Logger logger = LoggerFactory.getLogger(AudioStorageService.class);

    private final WordAudioRepository wordAudioRepository;

    @Value("${audio.storage.directory:./audio-data}")
    private String storageDir;

    private Path audioPath;

    public AudioStorageService(WordAudioRepository wordAudioRepository) {
        this.wordAudioRepository = wordAudioRepository;
    }

    @PostConstruct
    public void init() {
        audioPath = Paths.get(storageDir).toAbsolutePath();
        try {
            Files.createDirectories(audioPath);
            logger.info("音频存储目录已初始化: {}", audioPath);
        } catch (IOException e) {
            logger.error("无法创建音频存储目录: {}", audioPath, e);
        }
    }

    public byte[] loadAudio(String word) {
        return loadAudioByKey(word);
    }

    public byte[] loadAudioByKey(String audioKey) {
        // Try filesystem first
        Path filePath = resolveAudioFilePath(audioKey);
        if (Files.exists(filePath)) {
            try {
                return Files.readAllBytes(filePath);
            } catch (IOException e) {
                logger.warn("从文件系统读取音频失败: {}, 回退到数据库", audioKey);
            }
        }

        // Fallback to database (legacy LONGBLOB)
        return wordAudioRepository.findByWord(audioKey)
                .map(WordAudio::getAudioData)
                .orElse(null);
    }

    public void saveAudio(String word, byte[] audioData) {
        saveAudioByKey(word, audioData);
    }

    public boolean existsByAudioKey(String audioKey) {
        return loadAudioByKey(audioKey) != null;
    }

    public void deleteAudioByKey(String audioKey) {
        Path filePath = resolveAudioFilePath(audioKey);
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            logger.warn("删除音频文件失败: {}", audioKey, e);
        }

        wordAudioRepository.deleteByWord(audioKey);
    }

    public void saveAudioByKey(String audioKey, byte[] audioData) {
        // Save to filesystem
        Path filePath = resolveAudioFilePath(audioKey);
        try {
            Files.write(filePath, audioData);
            logger.info("音频已保存到文件系统: {} ({} 字节)", filePath, audioData.length);
        } catch (IOException e) {
            logger.error("保存音频到文件系统失败: {}", audioKey, e);
        }

        // Update database record (store path reference instead of blob)
        Optional<WordAudio> existing = wordAudioRepository.findByWord(audioKey);
        WordAudio audio;
        if (existing.isPresent()) {
            audio = existing.get();
            audio.setCreateTime(LocalDateTime.now());
        } else {
            audio = new WordAudio();
            audio.setWord(audioKey);
            audio.setCreateTime(LocalDateTime.now());
        }
        // Keep audioData in DB for backward compatibility during migration
        audio.setAudioData(audioData);
        wordAudioRepository.save(audio);
    }

    private Path resolveAudioFilePath(String audioKey) {
        return audioPath.resolve(buildSafeFileName(audioKey) + ".mp3");
    }

    private String buildSafeFileName(String audioKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(audioKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}

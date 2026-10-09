package com.songzy.dicserver.service;

import com.songzy.dicserver.model.WordAudio;
import com.songzy.dicserver.repository.WordAudioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AudioStorageServiceTest {

    @Mock
    private WordAudioRepository wordAudioRepository;

    @InjectMocks
    private AudioStorageService audioStorageService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(audioStorageService, "storageDir", tempDir.toString());
        audioStorageService.init();
    }

    @Test
    void saveAudioShouldWriteToFileSystem() {
        byte[] audioData = new byte[]{1, 2, 3, 4, 5};
        when(wordAudioRepository.findByWord("test")).thenReturn(Optional.empty());
        when(wordAudioRepository.save(any(WordAudio.class))).thenAnswer(i -> i.getArgument(0));

        audioStorageService.saveAudio("test", audioData);

        verify(wordAudioRepository).save(any(WordAudio.class));
    }

    @Test
    void saveAudioByKeyShouldWriteToFileSystemAndLoadByKey() {
        byte[] audioData = new byte[]{9, 8, 7};
        when(wordAudioRepository.findByWord("english_word:hello")).thenReturn(Optional.empty());
        when(wordAudioRepository.save(any(WordAudio.class))).thenAnswer(i -> i.getArgument(0));

        audioStorageService.saveAudioByKey("english_word:hello", audioData);

        byte[] loaded = audioStorageService.loadAudioByKey("english_word:hello");

        assertThat(loaded).isEqualTo(audioData);
        verify(wordAudioRepository).save(any(WordAudio.class));
    }

    @Test
    void saveAudioByKeyShouldNotAllowPathTraversalOutsideStorageDirectory() throws Exception {
        String audioKey = "../outside-" + UUID.randomUUID();
        byte[] audioData = new byte[]{6, 5, 4};
        Path escapedPath = tempDir.getParent().resolve(audioKey.substring(3) + ".mp3");

        try {
            when(wordAudioRepository.findByWord(audioKey)).thenReturn(Optional.empty());
            when(wordAudioRepository.save(any(WordAudio.class))).thenAnswer(i -> i.getArgument(0));

            audioStorageService.saveAudioByKey(audioKey, audioData);

            assertThat(Files.exists(escapedPath)).isFalse();
            assertThat(audioStorageService.loadAudioByKey(audioKey)).isEqualTo(audioData);
        } finally {
            Files.deleteIfExists(escapedPath);
        }
    }

    @Test
    void loadAudioShouldReturnNullWhenNotFound() {
        when(wordAudioRepository.findByWord("nonexistent")).thenReturn(Optional.empty());

        byte[] result = audioStorageService.loadAudio("nonexistent");

        assertThat(result).isNull();
    }

    @Test
    void deleteAudioByKeyShouldRemoveFilesystemAndDatabaseCopies() {
        byte[] audioData = new byte[]{1, 3, 5};
        when(wordAudioRepository.findByWord("english_word:hello")).thenReturn(Optional.empty());
        when(wordAudioRepository.save(any(WordAudio.class))).thenAnswer(i -> i.getArgument(0));

        audioStorageService.saveAudioByKey("english_word:hello", audioData);
        audioStorageService.deleteAudioByKey("english_word:hello");

        when(wordAudioRepository.findByWord("english_word:hello")).thenReturn(Optional.empty());
        assertThat(audioStorageService.loadAudioByKey("english_word:hello")).isNull();
        verify(wordAudioRepository).deleteByWord("english_word:hello");
    }
}

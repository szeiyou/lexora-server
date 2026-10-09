package com.songzy.dicserver.service;

import com.songzy.dicserver.model.EntryQueryResponse;
import com.songzy.dicserver.model.EntryResultType;
import com.songzy.dicserver.model.AudioGenerationTask;
import com.songzy.dicserver.model.AudioType;
import com.songzy.dicserver.model.WordResponse;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EntryAudioService {
    private static final Logger logger = LoggerFactory.getLogger(EntryAudioService.class);

    private final AudioStorageService audioStorageService;
    private final AudioKeyBuilder audioKeyBuilder;
    private final AudioGenerationTaskService audioGenerationTaskService;
    private final AudioGenerationExecutor audioGenerationExecutor;
    private final TtsService ttsService;

    public EntryAudioService(AudioStorageService audioStorageService,
                             AudioKeyBuilder audioKeyBuilder,
                             AudioGenerationTaskService audioGenerationTaskService,
                             AudioGenerationExecutor audioGenerationExecutor,
                             TtsService ttsService) {
        this.audioStorageService = audioStorageService;
        this.audioKeyBuilder = audioKeyBuilder;
        this.audioGenerationTaskService = audioGenerationTaskService;
        this.audioGenerationExecutor = audioGenerationExecutor;
        this.ttsService = ttsService;
    }

    public EntryQueryResponse enrichHeadwordAudio(EntryQueryResponse response) {
        if (response == null || response.getResultType() == null) {
            return response;
        }

        if (response.getResultType() == EntryResultType.ENGLISH_WORD && response.getEnglishWord() != null) {
            String headword = response.getEnglishWord().getWord();
            String fullReadingText = buildFullReadingSourceText(response.getEnglishWord());
            EntryQueryResponse.HeadwordAudio headwordAudio = resolveAudioReference(
                    AudioKeyBuilder.AudioKeyType.ENGLISH_WORD_HEADWORD,
                    AudioType.HEADWORD,
                    EntryResultType.ENGLISH_WORD,
                    headword,
                    headword
            );
            EntryQueryResponse.HeadwordAudio fullReadingAudio = resolveAudioReference(
                    AudioKeyBuilder.AudioKeyType.ENGLISH_WORD_FULL_READING,
                    AudioType.FULL_READING,
                    EntryResultType.ENGLISH_WORD,
                    headword,
                    fullReadingText
            );
            response.getEnglishWord().setHeadwordAudio(headwordAudio);
            response.getEnglishWord().setFullReadingAudio(fullReadingAudio);
            triggerEnglishWordExecution(headwordAudio, fullReadingAudio);
        }

        if (response.getResultType() == EntryResultType.ZH_TO_EN_TERM && response.getZhToEnTerm() != null
                && response.getZhToEnTerm().getCandidates() != null) {
            for (EntryQueryResponse.TermCandidate candidate : response.getZhToEnTerm().getCandidates()) {
                EntryQueryResponse.HeadwordAudio headwordAudio = resolveAudioReference(
                        AudioKeyBuilder.AudioKeyType.ZH_TO_EN_TERM_HEADWORD,
                        AudioType.HEADWORD,
                        EntryResultType.ZH_TO_EN_TERM,
                        candidate.getTerm(),
                        candidate.getTerm()
                );
                candidate.setHeadwordAudio(headwordAudio);
                triggerHeadwordExecution(headwordAudio);
            }
        }

        return response;
    }

    public byte[] getAudioByKey(String audioKey) {
        if (audioKey == null || audioKey.trim().isEmpty()) {
            return null;
        }
        return audioStorageService.loadAudioByKey(AudioKeyBuilder.normalizeAudioKey(audioKey));
    }

    private EntryQueryResponse.HeadwordAudio resolveAudioReference(AudioKeyBuilder.AudioKeyType audioKeyType,
                                                                  AudioType audioType,
                                                                  EntryResultType sourceType,
                                                                  String keyText,
                                                                  String sourceText) {
        String audioKey = audioKeyBuilder.build(audioKeyType, keyText);
        if (audioKey == null) {
            return null;
        }

        String normalizedSourceText = normalizeSourceText(sourceText);
        byte[] existingAudio = audioStorageService.loadAudioByKey(audioKey);
        if (existingAudio == null) {
            registerAudioTask(audioKey, audioType, sourceType, normalizedSourceText);
        } else {
            synchronizeFullReadingTask(audioKey, audioType, sourceType, normalizedSourceText);
        }

        EntryQueryResponse.HeadwordAudio headwordAudio = new EntryQueryResponse.HeadwordAudio();
        headwordAudio.setAudioKey(audioKey);
        headwordAudio.setAudioUrl(audioKeyBuilder.audioUrl(audioKey));
        return headwordAudio;
    }

    private void registerAudioTask(String audioKey,
                                   AudioType audioType,
                                   EntryResultType sourceType,
                                   String sourceText) {
        try {
            audioGenerationTaskService.ensureTask(buildTask(audioKey, audioType, sourceType, sourceText));
        } catch (RuntimeException ex) {
            logger.warn("Failed to register audio generation task for audioKey={} audioType={} sourceType={}",
                    audioKey, audioType, sourceType, ex);
        }
    }

    private void synchronizeFullReadingTask(String audioKey,
                                            AudioType audioType,
                                            EntryResultType sourceType,
                                            String normalizedSourceText) {
        if (audioType != AudioType.FULL_READING) {
            return;
        }

        audioGenerationTaskService.findTask(audioKey).ifPresentOrElse(existingTask -> {
            if (!Objects.equals(
                    AudioKeyBuilder.normalizeText(existingTask.getSourceText()),
                    AudioKeyBuilder.normalizeText(normalizedSourceText))) {
                audioStorageService.deleteAudioByKey(audioKey);
                registerAudioTask(audioKey, audioType, sourceType, normalizedSourceText);
            }
        }, () -> {
            registerAudioTask(audioKey, audioType, sourceType, normalizedSourceText);
            audioGenerationTaskService.markReady(audioKey);
        });
    }

    private void triggerEnglishWordExecution(EntryQueryResponse.HeadwordAudio headwordAudio,
                                             EntryQueryResponse.HeadwordAudio fullReadingAudio) {
        if (headwordAudio == null) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            AudioGenerationExecutor.ExecutionResult headwordResult =
                    audioGenerationExecutor.execute(headwordAudio.getAudioKey());
            if (fullReadingAudio != null && isHeadwordReady(headwordAudio.getAudioKey(), headwordResult)) {
                audioGenerationExecutor.execute(fullReadingAudio.getAudioKey());
            }
        });
    }

    private void triggerHeadwordExecution(EntryQueryResponse.HeadwordAudio headwordAudio) {
        if (headwordAudio == null) {
            return;
        }

        CompletableFuture.runAsync(() -> audioGenerationExecutor.execute(headwordAudio.getAudioKey()));
    }

    private boolean isHeadwordReady(String audioKey, AudioGenerationExecutor.ExecutionResult result) {
        return result == AudioGenerationExecutor.ExecutionResult.READY
                || audioStorageService.existsByAudioKey(audioKey)
                || audioGenerationTaskService.hasStatus(audioKey, com.songzy.dicserver.model.AudioGenerationStatus.READY);
    }

    private AudioGenerationTask buildTask(String audioKey,
                                          AudioType audioType,
                                          EntryResultType sourceType,
                                          String sourceText) {
        AudioGenerationTask task = new AudioGenerationTask();
        task.setAudioKey(audioKey);
        task.setAudioType(audioType);
        task.setSourceType(sourceType);
        task.setSourceText(normalizeSourceText(sourceText));
        return task;
    }

    private String normalizeSourceText(String sourceText) {
        if (sourceText == null) {
            return null;
        }
        String normalized = sourceText.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }

    private String buildFullReadingSourceText(EntryQueryResponse.EnglishWordPayload payload) {
        WordResponse response = new WordResponse();
        response.setDefinitions(payload.getDefinitions() == null ? List.of()
                : payload.getDefinitions().stream().map(definition -> {
                    WordResponse.Definition mapped = new WordResponse.Definition();
                    mapped.setPartOfSpeech(definition.getPartOfSpeech());
                    mapped.setMeaning(definition.getMeaning());
                    mapped.setSynonyms(definition.getSynonyms());
                    return mapped;
                }).toList());
        response.setExamples(payload.getExamples() == null ? List.of()
                : payload.getExamples().stream().map(example -> {
                    WordResponse.Example mapped = new WordResponse.Example();
                    mapped.setSentence(example.getSentence());
                    mapped.setTranslation(example.getTranslation());
                    return mapped;
                }).toList());
        response.setExtension(payload.getExtension());
        return ttsService.buildReadingText(normalizeSourceText(payload.getWord()), response);
    }
}

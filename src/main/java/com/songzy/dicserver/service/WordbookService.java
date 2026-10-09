package com.songzy.dicserver.service;

import com.songzy.dicserver.model.Wordbook;
import com.songzy.dicserver.model.WordbookEntry;
import com.songzy.dicserver.model.EnglishWordSnapshot;
import com.songzy.dicserver.model.EntryQueryResponse;
import com.songzy.dicserver.repository.AppUserRepository;
import com.songzy.dicserver.repository.WordbookRepository;
import com.songzy.dicserver.repository.WordbookEntryRepository;
import com.songzy.dicserver.repository.EnglishWordSnapshotRepository;
import com.songzy.dicserver.exception.BusinessException;
import com.songzy.dicserver.dto.WordbookEntryDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.HashSet;
import java.io.IOException;
import org.springframework.transaction.annotation.Isolation;

@Service
public class WordbookService {

    @Autowired
    private WordbookRepository wordbookRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private WordbookEntryRepository entryRepository;

    @Autowired
    private EnglishWordSnapshotRepository englishWordSnapshotRepository;

    @Autowired
    private QueryTextNormalizer queryTextNormalizer;

    @Autowired
    private ObjectMapper objectMapper;

    public List<Wordbook> getAllWordbooks(Long userId) {
        return wordbookRepository.findAllByUserIdOrderByCreateTimeDesc(userId);
    }

    public Wordbook getWordbookById(Long userId, Long id) {
        return wordbookRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException("单词本不存在", "WORDBOOK_NOT_FOUND"));
    }

    public Wordbook createWordbook(Long userId, String name) {
        if (wordbookRepository.existsByUserIdAndName(userId, name)) {
            throw new BusinessException("该名称的单词本已存在", "DUPLICATE_NAME");
        }
        try {
            return doCreateWordbook(userId, name);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("该名称的单词本已存在", "DUPLICATE_NAME");
        }
    }

    public Wordbook updateWordbook(Long userId, Long id, String newName) {
        Wordbook wordbook = wordbookRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException("单词本不存在", "WORDBOOK_NOT_FOUND"));
        if (!wordbook.getName().equals(newName) && wordbookRepository.existsByUserIdAndName(userId, newName)) {
            throw new BusinessException("该名称的单词本已存在", "DUPLICATE_NAME");
        }
        try {
            return doUpdateWordbook(wordbook, newName);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("该名称的单词本已存在", "DUPLICATE_NAME");
        }
    }

    @Transactional
    public void deleteWordbook(Long userId, Long id) {
        if (!wordbookRepository.existsByIdAndUserId(id, userId)) {
            throw new BusinessException("单词本不存在", "WORDBOOK_NOT_FOUND");
        }
        entryRepository.deleteByWordbookIdAndUserId(id, userId);
        wordbookRepository.deleteById(id);
    }

    public List<WordbookEntryDTO> getWordbookEntries(Long userId, Long wordbookId) {
        if (!wordbookRepository.existsByIdAndUserId(wordbookId, userId)) {
            throw new BusinessException("单词本不存在", "WORDBOOK_NOT_FOUND");
        }
        return entryRepository.findByWordbookIdAndUserId(wordbookId, userId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public void addWordsToWordbook(Long userId, Long wordbookId, List<String> words) {
        Wordbook wordbook = wordbookRepository.findByIdAndUserId(wordbookId, userId)
                .orElseThrow(() -> new BusinessException("单词本不存在", "WORDBOOK_NOT_FOUND"));

        Map<String, String> requestedWordsByNormalizedWord = new LinkedHashMap<>();
        List<String> invalidWords = new ArrayList<>();
        List<String> duplicateWords = new ArrayList<>();

        Set<String> existingNormalizedWords = new HashSet<>(
                entryRepository.findNormalizedWordsByWordbookIdAndUserId(wordbookId, userId)
        );

        for (String word : words) {
            String normalizedWord = queryTextNormalizer.normalizeForLookup(word);
            if (normalizedWord == null) {
                invalidWords.add(word);
                continue;
            }

            if (existingNormalizedWords.contains(normalizedWord)) {
                duplicateWords.add(word);
                continue;
            }

            requestedWordsByNormalizedWord.putIfAbsent(normalizedWord, word);
        }

        if (!invalidWords.isEmpty()) {
            throw new BusinessException(
                "以下单词未在词典中查询过: " + String.join(", ", invalidWords),
                "WORDS_NOT_FOUND"
            );
        }

        if (!duplicateWords.isEmpty()) {
            throw new BusinessException(
                "以下单词已在单词本中: " + String.join(", ", duplicateWords),
                "DUPLICATE_WORDS"
            );
        }

        List<WordbookEntry> newEntries = new ArrayList<>();
        for (Map.Entry<String, String> requestedWord : requestedWordsByNormalizedWord.entrySet()) {
            String normalizedWord = requestedWord.getKey();
            String rawWord = requestedWord.getValue();
            String fingerprint = queryTextNormalizer.fingerprintForLookup(normalizedWord);

            EnglishWordSnapshot snapshot = englishWordSnapshotRepository
                    .findByNormalizedHeadwordFingerprint(fingerprint)
                    .orElse(null);
            if (snapshot == null) {
                invalidWords.add(rawWord);
                continue;
            }

            try {
                newEntries.add(createWordbookEntry(normalizedWord, snapshot, wordbook));
            } catch (IOException e) {
                throw new BusinessException(
                        String.format("解析单词 '%s' 的数据时出错", rawWord),
                        "PARSE_ERROR"
                );
            }
        }

        if (!invalidWords.isEmpty()) {
            throw new BusinessException(
                    "以下单词未在词典中查询过: " + String.join(", ", invalidWords),
                    "WORDS_NOT_FOUND"
            );
        }

        try {
            doAddWordsToWordbook(newEntries);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(
                "以下单词已在单词本中: " + String.join(", ", requestedWordsByNormalizedWord.values()),
                "DUPLICATE_WORDS"
            );
        }
    }

    @Transactional
    public void removeWordsFromWordbook(Long userId, Long wordbookId, List<String> words) {
        if (!wordbookRepository.existsByIdAndUserId(wordbookId, userId)) {
            throw new BusinessException("单词本不存在", "WORDBOOK_NOT_FOUND");
        }

        List<String> normalizedWords = words.stream()
                .map(queryTextNormalizer::normalizeForLookup)
                .filter(word -> word != null && !word.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        if (normalizedWords.isEmpty()) {
            return;
        }

        entryRepository.deleteByWordbookIdAndUserIdAndNormalizedWordIn(wordbookId, userId, normalizedWords);
    }

    @Transactional
    public void clearWordbook(Long userId, Long wordbookId) {
        if (!wordbookRepository.existsByIdAndUserId(wordbookId, userId)) {
            throw new BusinessException("单词本不存在", "WORDBOOK_NOT_FOUND");
        }
        entryRepository.deleteByWordbookIdAndUserId(wordbookId, userId);
    }

    private WordbookEntryDTO convertToDTO(WordbookEntry entry) {
        WordbookEntryDTO dto = new WordbookEntryDTO();
        dto.setWord(entry.getWord());
        dto.setPartOfSpeech(entry.getPartOfSpeech());
        dto.setMeaning(entry.getMeaning());
        dto.setCreateTime(entry.getCreateTime());
        return dto;
    }

    /**
     * 查找包含指定单词的所有单词本ID
     * @param word 要查找的单词
     * @return 包含该单词的单词本ID列表
     */
    public List<Long> findWordbooksContainingWord(Long userId, String word) {
        String normalizedWord = queryTextNormalizer.normalizeForLookup(word);
        if (normalizedWord == null) {
            return List.of();
        }
        return entryRepository.findWordbookIdsByNormalizedWordAndUserId(normalizedWord, userId);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    protected Wordbook doCreateWordbook(Long userId, String name) {
        Wordbook wordbook = new Wordbook();
        wordbook.setUser(appUserRepository.getReferenceById(userId));
        wordbook.setName(name);
        return wordbookRepository.save(wordbook);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    protected Wordbook doUpdateWordbook(Wordbook wordbook, String newName) {
        wordbook.setName(newName);
        return wordbookRepository.save(wordbook);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    protected void doAddWordsToWordbook(List<WordbookEntry> newEntries) {
        entryRepository.saveAll(newEntries);
    }

    private WordbookEntry createWordbookEntry(String normalizedWord,
                                              EnglishWordSnapshot snapshot,
                                              Wordbook wordbook) throws IOException {
        EntryQueryResponse.EnglishWordPayload payload = objectMapper.readValue(
                snapshot.getWordDataJson(),
                EntryQueryResponse.EnglishWordPayload.class
        );

        Set<String> partsOfSpeech = payload.getDefinitions() == null
                ? Set.of()
                : payload.getDefinitions().stream()
                .map(EntryQueryResponse.Definition::getPartOfSpeech)
                .filter(partOfSpeech -> partOfSpeech != null && !partOfSpeech.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        String meaning = "";
        if (payload.getDefinitions() != null) {
            for (EntryQueryResponse.Definition definition : payload.getDefinitions()) {
                if (definition.getMeaning() != null && !definition.getMeaning().isBlank()) {
                    meaning = definition.getMeaning();
                    break;
                }
            }
        }

        WordbookEntry entry = new WordbookEntry();
        entry.setWordbook(wordbook);
        entry.setWord(snapshot.getHeadword());
        entry.setNormalizedWord(normalizedWord);
        entry.setPartOfSpeech(String.join(", ", partsOfSpeech));
        entry.setMeaning(meaning);
        return entry;
    }
}

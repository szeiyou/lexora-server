package com.songzy.dicserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songzy.dicserver.exception.BusinessException;
import com.songzy.dicserver.model.AppUser;
import com.songzy.dicserver.model.EnglishWordSnapshot;
import com.songzy.dicserver.model.EntryQueryResponse;
import com.songzy.dicserver.model.UserStatus;
import com.songzy.dicserver.model.Wordbook;
import com.songzy.dicserver.model.WordbookEntry;
import com.songzy.dicserver.repository.AppUserRepository;
import com.songzy.dicserver.repository.EnglishWordSnapshotRepository;
import com.songzy.dicserver.repository.WordbookEntryRepository;
import com.songzy.dicserver.repository.WordbookRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WordbookServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private WordbookRepository wordbookRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private WordbookEntryRepository entryRepository;

    @Mock
    private EnglishWordSnapshotRepository englishWordSnapshotRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Spy
    private QueryTextNormalizer queryTextNormalizer = new QueryTextNormalizer();

    @InjectMocks
    private WordbookService wordbookService;

    @Test
    void createWordbookShouldRejectDuplicateName() {
        when(wordbookRepository.existsByUserIdAndName(USER_ID, "CET-4")).thenReturn(true);

        assertThatThrownBy(() -> wordbookService.createWordbook(USER_ID, "CET-4"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该名称的单词本已存在")
                .extracting("code")
                .isEqualTo("DUPLICATE_NAME");

        verify(wordbookRepository, never()).save(any(Wordbook.class));
    }

    @Test
    void createWordbookShouldConvertUniqueConstraintViolationToBusinessException() {
        when(wordbookRepository.existsByUserIdAndName(USER_ID, "CET-4")).thenReturn(false);
        when(appUserRepository.getReferenceById(USER_ID)).thenReturn(user(USER_ID, "alice"));
        when(wordbookRepository.save(any(Wordbook.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> wordbookService.createWordbook(USER_ID, "CET-4"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该名称的单词本已存在")
                .extracting("code")
                .isEqualTo("DUPLICATE_NAME");
    }

    @Test
    void getWordbookByIdShouldRejectAccessToAnotherUsersWordbook() {
        when(wordbookRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wordbookService.getWordbookById(USER_ID, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("单词本不存在")
                .extracting("code")
                .isEqualTo("WORDBOOK_NOT_FOUND");
    }

    @Test
    void createWordbookShouldCheckDuplicateNameWithinSameUserOnly() {
        when(wordbookRepository.existsByUserIdAndName(USER_ID, "Default")).thenReturn(false);
        when(appUserRepository.getReferenceById(USER_ID)).thenReturn(user(USER_ID, "alice"));
        when(wordbookRepository.save(any(Wordbook.class))).thenAnswer(invocation -> invocation.getArgument(0));

        wordbookService.createWordbook(USER_ID, "Default");

        verify(wordbookRepository).existsByUserIdAndName(USER_ID, "Default");
    }

    @Test
    void updateWordbookShouldRejectDuplicateName() {
        Wordbook existing = wordbook(1L, "CET-4");

        when(wordbookRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(existing));
        when(wordbookRepository.existsByUserIdAndName(USER_ID, "TOEFL")).thenReturn(true);

        assertThatThrownBy(() -> wordbookService.updateWordbook(USER_ID, 1L, "TOEFL"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该名称的单词本已存在")
                .extracting("code")
                .isEqualTo("DUPLICATE_NAME");

        verify(wordbookRepository, never()).save(existing);
    }

    @Test
    void updateWordbookShouldAllowKeepingCurrentName() {
        Wordbook existing = wordbook(1L, "CET-4");

        when(wordbookRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(existing));
        when(wordbookRepository.save(existing)).thenReturn(existing);

        Wordbook result = wordbookService.updateWordbook(USER_ID, 1L, "CET-4");

        assertThat(result).isSameAs(existing);
        verify(wordbookRepository).save(existing);
    }

    @Test
    void updateWordbookShouldConvertUniqueConstraintViolationToBusinessException() {
        Wordbook existing = wordbook(1L, "CET-4");

        when(wordbookRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(existing));
        when(wordbookRepository.existsByUserIdAndName(USER_ID, "TOEFL")).thenReturn(false);
        when(wordbookRepository.save(any(Wordbook.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> wordbookService.updateWordbook(USER_ID, 1L, "TOEFL"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该名称的单词本已存在")
                .extracting("code")
                .isEqualTo("DUPLICATE_NAME");
    }

    @Test
    void createWordbookShouldNotUseStringInternLocking() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/songzy/dicserver/service/WordbookService.java"));

        assertThat(source).doesNotContain("intern(");
    }

    @Test
    void createWordbookShouldNotKeepStaticWordbookLockMap() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/songzy/dicserver/service/WordbookService.java"));

        assertThat(source).doesNotContain("wordbookLocks");
        assertThat(source).doesNotContain("getWordbookLock(");
    }

    @Test
    void deleteWordbookShouldDeleteEntriesBeforeDeletingWordbook() {
        when(wordbookRepository.existsByIdAndUserId(1L, USER_ID)).thenReturn(true);

        wordbookService.deleteWordbook(USER_ID, 1L);

        verify(entryRepository).deleteByWordbookIdAndUserId(1L, USER_ID);
        verify(wordbookRepository).deleteById(1L);
    }

    @Test
    void deleteWordbookShouldRejectMissingWordbook() {
        when(wordbookRepository.existsByIdAndUserId(1L, USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> wordbookService.deleteWordbook(USER_ID, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("单词本不存在")
                .extracting("code")
                .isEqualTo("WORDBOOK_NOT_FOUND");

        verify(entryRepository, never()).deleteByWordbookIdAndUserId(any(Long.class), any(Long.class));
        verify(wordbookRepository, never()).deleteById(any(Long.class));
    }

    @Test
    void addWordsToWordbookShouldUseSnapshotCanonicalWordAndNormalizedWord() throws Exception {
        Wordbook wordbook = wordbook(1L, "CET-4");
        EnglishWordSnapshot snapshot = snapshot("Visible", "visible", "{\"word\":\"Visible\"}");

        when(wordbookRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(wordbook));
        when(entryRepository.findNormalizedWordsByWordbookIdAndUserId(1L, USER_ID)).thenReturn(List.of());
        when(englishWordSnapshotRepository.findByNormalizedHeadwordFingerprint(
                queryTextNormalizer.fingerprintForLookup("visible")
        )).thenReturn(Optional.of(snapshot));
        when(objectMapper.readValue(snapshot.getWordDataJson(), EntryQueryResponse.EnglishWordPayload.class))
                .thenReturn(parsedWordPayload("adj.", "可见的"));

        wordbookService.addWordsToWordbook(USER_ID, 1L, List.of("  VISIBLE  "));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WordbookEntry>> entriesCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(entryRepository).saveAll(entriesCaptor.capture());
        WordbookEntry saved = entriesCaptor.getValue().get(0);
        assertThat(saved.getWord()).isEqualTo("Visible");
        assertThat(saved.getNormalizedWord()).isEqualTo("visible");
        assertThat(saved.getPartOfSpeech()).isEqualTo("adj.");
        assertThat(saved.getMeaning()).isEqualTo("可见的");
    }

    @Test
    void addWordsToWordbookShouldRejectMissingSnapshots() {
        Wordbook wordbook = wordbook(1L, "CET-4");

        when(wordbookRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(wordbook));
        when(entryRepository.findNormalizedWordsByWordbookIdAndUserId(1L, USER_ID)).thenReturn(List.of());
        when(englishWordSnapshotRepository.findByNormalizedHeadwordFingerprint(
                queryTextNormalizer.fingerprintForLookup("visible")
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wordbookService.addWordsToWordbook(USER_ID, 1L, List.of("VISIBLE")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("以下单词未在词典中查询过")
                .extracting("code")
                .isEqualTo("WORDS_NOT_FOUND");
    }

    @Test
    void addWordsToWordbookShouldRejectDuplicatesByNormalizedWord() {
        Wordbook wordbook = wordbook(1L, "CET-4");

        when(wordbookRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(wordbook));
        when(entryRepository.findNormalizedWordsByWordbookIdAndUserId(1L, USER_ID)).thenReturn(List.of("visible"));

        assertThatThrownBy(() -> wordbookService.addWordsToWordbook(USER_ID, 1L, List.of("VISIBLE")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("以下单词已在单词本中")
                .extracting("code")
                .isEqualTo("DUPLICATE_WORDS");
    }

    @Test
    void addWordsToWordbookShouldConvertDuplicateInsertViolationToBusinessException() throws Exception {
        Wordbook wordbook = wordbook(1L, "CET-4");
        EnglishWordSnapshot snapshot = snapshot("Visible", "visible", "{\"word\":\"Visible\"}");

        when(wordbookRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(wordbook));
        when(entryRepository.findNormalizedWordsByWordbookIdAndUserId(1L, USER_ID)).thenReturn(List.of());
        when(englishWordSnapshotRepository.findByNormalizedHeadwordFingerprint(
                queryTextNormalizer.fingerprintForLookup("visible")
        )).thenReturn(Optional.of(snapshot));
        when(objectMapper.readValue(snapshot.getWordDataJson(), EntryQueryResponse.EnglishWordPayload.class))
                .thenReturn(parsedWordPayload("adj.", "可见的"));
        when(entryRepository.saveAll(any())).thenThrow(new DataIntegrityViolationException("duplicate entry"));

        assertThatThrownBy(() -> wordbookService.addWordsToWordbook(USER_ID, 1L, List.of("visible")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("以下单词已在单词本中")
                .extracting("code")
                .isEqualTo("DUPLICATE_WORDS");
    }

    @Test
    void removeWordsFromWordbookShouldDeleteByNormalizedWord() {
        when(wordbookRepository.existsByIdAndUserId(1L, USER_ID)).thenReturn(true);

        wordbookService.removeWordsFromWordbook(USER_ID, 1L, List.of(" Visible ", "VISIBLE"));

        verify(entryRepository).deleteByWordbookIdAndUserIdAndNormalizedWordIn(1L, USER_ID, List.of("visible"));
    }

    @Test
    void findWordbooksContainingWordShouldUseNormalizedLookup() {
        when(entryRepository.findWordbookIdsByNormalizedWordAndUserId("visible", USER_ID)).thenReturn(List.of(1L, 2L));

        List<Long> result = wordbookService.findWordbooksContainingWord(USER_ID, "  VISIBLE  ");

        assertThat(result).containsExactly(1L, 2L);
        verify(entryRepository).findWordbookIdsByNormalizedWordAndUserId("visible", USER_ID);
    }

    @Test
    void duplicateConstraintTranslationMethodsShouldNotBeTransactional() throws Exception {
        assertThat(WordbookService.class.getDeclaredMethod("createWordbook", Long.class, String.class)
                .isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(WordbookService.class.getDeclaredMethod("updateWordbook", Long.class, Long.class, String.class)
                .isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(WordbookService.class.getDeclaredMethod("addWordsToWordbook", Long.class, Long.class, List.class)
                .isAnnotationPresent(Transactional.class)).isFalse();
    }

    private Wordbook wordbook(Long id, String name) {
        Wordbook wordbook = new Wordbook();
        wordbook.setId(id);
        wordbook.setName(name);
        wordbook.setUser(user(USER_ID, "alice"));
        return wordbook;
    }

    private AppUser user(Long id, String username) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername(username);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private EnglishWordSnapshot snapshot(String headword, String normalizedHeadword, String wordDataJson) {
        EnglishWordSnapshot snapshot = new EnglishWordSnapshot();
        snapshot.setHeadword(headword);
        snapshot.setNormalizedHeadword(normalizedHeadword);
        snapshot.setNormalizedHeadwordFingerprint(queryTextNormalizer.fingerprintForLookup(normalizedHeadword));
        snapshot.setWordDataJson(wordDataJson);
        return snapshot;
    }

    private EntryQueryResponse.EnglishWordPayload parsedWordPayload(String partOfSpeech, String meaning) {
        EntryQueryResponse.EnglishWordPayload payload = new EntryQueryResponse.EnglishWordPayload();
        payload.setWord("Visible");

        EntryQueryResponse.Definition definition = new EntryQueryResponse.Definition();
        definition.setPartOfSpeech(partOfSpeech);
        definition.setMeaning(meaning);
        definition.setSynonyms(List.of());

        payload.setDefinitions(List.of(definition));
        return payload;
    }
}

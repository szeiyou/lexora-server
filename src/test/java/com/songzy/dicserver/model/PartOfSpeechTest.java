package com.songzy.dicserver.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songzy.dicserver.dto.WordbookEntryDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PartOfSpeechTest {
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "noun | n.", "名词 | n.", "Verb | v.", "动词 | v.",
            "adjective | adj.", "形容词 | adj.", "adverb | adv.", "副词 | adv.",
            "pronoun | pron.", "代词 | pron.", "preposition | prep.", "介词 | prep.",
            "conjunction | conj.", "连词 | conj.", "interjection | interj.", "感叹词 | interj.",
            "article | art.", "冠词 | art.", "determiner | det.", "限定词 | det.",
            "numeral | num.", "数词 | num.", "auxiliary verb | aux.", "助动词 | aux.",
            "transitive verb | vt.", "及物动词 | vt.", "intransitive verb | vi.", "不及物动词 | vi.",
            "noun, verb | n., v.", "名词，动词 | n., v.", "名词、动词 | n., v.",
            "noun and verb | n., v.", "n./v. | n., v.", "n. v. | n., v.",
            "adj | adj.", "n. | n.", "n., v. | n., v.", "noun, 名词 | n."
    })
    void normalizesCommonEnglishChineseAndCombinedLabels(String input, String expected) {
        assertThat(PartOfSpeech.normalize(input)).isEqualTo(expected);
        assertThat(PartOfSpeech.normalize(expected)).isEqualTo(expected);
    }

    @Test
    void preservesMissingAndUnrecognizedLabels() {
        assertThat(PartOfSpeech.normalize(null)).isNull();
        assertThat(PartOfSpeech.normalize("")).isEmpty();
        assertThat(PartOfSpeech.normalize("unfamiliar label")).isEqualTo("unfamiliar label");
    }

    @Test
    void normalizesStoredPayloadsAndHistoryWhenDeserialized() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        EntryQueryResponse response = mapper.readValue("""
                {
                  "englishWord": {"definitions": [{"partOfSpeech": "名词，动词", "meaning": "测试"}]},
                  "zhToEnTerm": {"candidates": [{"partOfSpeech": "adjective"}]}
                }
                """, EntryQueryResponse.class);

        assertThat(response.getEnglishWord().getDefinitions().getFirst().getPartOfSpeech()).isEqualTo("n., v.");
        assertThat(response.getEnglishWord().getDefinitions().getFirst().getMeaning()).isEqualTo("测试");
        assertThat(response.getZhToEnTerm().getCandidates().getFirst().getPartOfSpeech()).isEqualTo("adj.");
        assertThat(mapper.writeValueAsString(response)).contains("\"partOfSpeech\":\"n., v.\"");

        EntryQueryResponse.EnglishWordPayload snapshot = mapper.readValue(
                "{\"definitions\":[{\"partOfSpeech\":\"noun\"}]}", EntryQueryResponse.EnglishWordPayload.class);
        assertThat(snapshot.getDefinitions().getFirst().getPartOfSpeech()).isEqualTo("n.");
    }

    @Test
    void normalizesExistingWordbookEntriesAndLegacyWordDefinitions() {
        WordbookEntryDTO dto = new WordbookEntryDTO();
        dto.setPartOfSpeech("名词, verb");
        assertThat(dto.getPartOfSpeech()).isEqualTo("n., v.");

        WordResponse.Definition definition = new WordResponse.Definition();
        definition.setPartOfSpeech("形容词");
        assertThat(definition.getPartOfSpeech()).isEqualTo("adj.");
    }
}

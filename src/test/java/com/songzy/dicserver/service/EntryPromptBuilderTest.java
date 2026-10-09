package com.songzy.dicserver.service;

import com.songzy.dicserver.model.EntryResultType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntryPromptBuilderTest {

    private final EntryPromptBuilder entryPromptBuilder = new EntryPromptBuilder();

    @Test
    void buildPromptShouldDescribeEnglishWordQualityContract() {
        String prompt = entryPromptBuilder.buildPrompt(EntryResultType.ENGLISH_WORD);

        assertThat(prompt).contains("ROLE & OUTPUT RULES");
        assertThat(prompt).contains("COMMON LANGUAGE RULES");
        assertThat(prompt).contains("RESULT-TYPE SPECIFIC QUALITY RULES");
        assertThat(prompt).contains("JSON STRUCTURE REQUIREMENTS");
        assertThat(prompt).contains("definitions[].meaning must be Simplified Chinese");
        assertThat(prompt).contains("etymology must be Simplified Chinese");
        assertThat(prompt).contains("extension must be Simplified Chinese");
        assertThat(prompt).contains("extension must first explain practical usage, register, boundaries, or common mistakes.");
        assertThat(prompt).contains("common collocations or typical contexts");
        assertThat(prompt).contains("extension must finally add concise memory aids such as roots, associations, or near-synonym distinctions.");
        assertThat(prompt).containsSubsequence(
                "extension must first explain practical usage, register, boundaries, or common mistakes.",
                "extension must then add common collocations or typical contexts.",
                "extension must finally add concise memory aids such as roots, associations, or near-synonym distinctions."
        );
        assertThat(prompt).doesNotContain("extension must first explain practical usage, then add concise memory aids");
        assertThat(prompt).doesNotContain("headwordAudio");
        assertThat(prompt).doesNotContain("fullReadingAudio");
        assertThat(prompt).doesNotContain("audioKey");
        assertThat(prompt).doesNotContain("audioUrl");
        assertThat(prompt).doesNotContain("The current user input is:");
        assertThat(prompt).doesNotContain("phenomenon");
        assertThat(prompt).containsSubsequence(
                "ENGLISH_WORD payload schema:",
                "\"word\": \"...\""
        );
        assertThat(prompt).doesNotContain("""
                ENGLISH_WORD payload schema:
                {
                  "resultType": "ENGLISH_WORD"
                """);
    }

    @Test
    void buildPromptShouldDescribeSimplifiedChineseRequirementsForOtherResultTypes() {
        String autoPrompt = entryPromptBuilder.buildPrompt(null);

        assertThat(autoPrompt).contains("First classify the input");
        assertThat(autoPrompt).contains("ENGLISH_WORD");
        assertThat(autoPrompt).contains("ZH_TO_EN_TERM");
        assertThat(autoPrompt).contains("SENTENCE_TRANSLATION");
        assertThat(autoPrompt).contains("\"query\"");
        assertThat(autoPrompt).contains("\"payload\"");
        assertThat(autoPrompt).contains("usageTip must be Simplified Chinese");
        assertThat(autoPrompt).contains("coreMeaning must be Simplified Chinese");
        assertThat(autoPrompt).contains("grammarBreakdown must be Simplified Chinese");
        assertThat(autoPrompt).contains("keyPhrases[].explanation must be Simplified Chinese");
        assertThat(autoPrompt).contains("learningNotes must be Simplified Chinese");
        assertThat(autoPrompt).doesNotContain("headwordAudio");
        assertThat(autoPrompt).doesNotContain("fullReadingAudio");
        assertThat(autoPrompt).doesNotContain("audioKey");
        assertThat(autoPrompt).doesNotContain("audioUrl");
        assertThat(autoPrompt).containsSubsequence(
                "ZH_TO_EN_TERM payload schema:",
                "\"input\": \"...\""
        );
        assertThat(autoPrompt).doesNotContain("""
                ZH_TO_EN_TERM payload schema:
                {
                  "resultType": "ZH_TO_EN_TERM"
                """);
    }

    @Test
    void buildPromptShouldRequireSentenceTranslationAnalysisToTargetEnglishSentence() {
        String prompt = entryPromptBuilder.buildPrompt(EntryResultType.SENTENCE_TRANSLATION);

        assertThat(prompt).contains("When the sentence translation involves English");
        assertThat(prompt).contains("grammarBreakdown, keyPhrases, alternativeExpressions, and learningNotes must analyze the English sentence");
        assertThat(prompt).contains("If sourceLanguage is zh and targetLanguage is en, analyze translatedSentence.");
        assertThat(prompt).contains("If sourceLanguage is en and targetLanguage is zh, analyze sourceSentence.");
        assertThat(prompt).contains("Do not analyze the Chinese sentence structure when an English sentence is available for learning analysis.");
    }

    @Test
    void wordPromptsShouldRequireAbbreviatedPartOfSpeech() {
        for (EntryResultType type : new EntryResultType[] {null, EntryResultType.ENGLISH_WORD, EntryResultType.ZH_TO_EN_TERM}) {
            String prompt = entryPromptBuilder.buildPrompt(type);
            assertThat(prompt).contains("Every partOfSpeech field must use English dictionary abbreviations only");
            assertThat(prompt).contains("n., v., vt., vi., adj., adv., pron., prep., conj., interj., art., det., num., aux.");
            assertThat(prompt).contains("Never use full English labels");
            assertThat(prompt).contains("or partOfSpeech label");
        }
    }

    @Test
    void buildPromptShouldRejectTextTranslationResultType() {
        assertThatThrownBy(() -> entryPromptBuilder.buildPrompt(EntryResultType.TEXT_TRANSLATION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TEXT_TRANSLATION");
    }
}

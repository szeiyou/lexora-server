package com.songzy.dicserver.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextTranslationPromptBuilderTest {

    private final TextTranslationPromptBuilder promptBuilder = new TextTranslationPromptBuilder();

    @Test
    void buildPromptShouldDemandStrictJsonSchemaWithAllRequiredFields() {
        String prompt = promptBuilder.buildPrompt("auto", "en");

        assertThat(prompt).contains("Return only valid JSON");
        assertThat(prompt).contains("strict JSON schema");
        assertThat(prompt).contains("\"text\"");
        assertThat(prompt).contains("\"normalizedText\"");
        assertThat(prompt).contains("\"sourceLanguage\"");
        assertThat(prompt).contains("\"targetLanguage\"");
        assertThat(prompt).contains("sourceLanguage must be \"zh or en\"");
        assertThat(prompt).contains("targetLanguage must be \"zh or en\"");
        assertThat(prompt).contains("\"translatedText\"");
        assertThat(prompt).contains("\"segments\"");
        assertThat(prompt).contains("\"keyPhrases\"");
        assertThat(prompt).contains("\"notes\"");
        assertThat(prompt).contains("\"sourceText\"");
        assertThat(prompt).contains("\"translatedText\"");
        assertThat(prompt).contains("\"note\"");
        assertThat(prompt).doesNotContain("\"payload\"");
        assertThat(prompt).doesNotContain("\"resultType\"");
    }

    @Test
    void buildPromptShouldInstructAutoDetectionWhileForcingRequestedTargetLanguage() {
        String prompt = promptBuilder.buildPrompt("auto", "en");

        assertThat(prompt).contains("Detect whether the source text is zh or en");
        assertThat(prompt).contains("Set sourceLanguage to the detected language");
        assertThat(prompt).contains("Set targetLanguage to \"en\"");
    }

    @Test
    void buildPromptShouldHonorExplicitRequestedSourceLanguage() {
        String prompt = promptBuilder.buildPrompt("zh", "en");

        assertThat(prompt).contains("Treat the source text as zh");
        assertThat(prompt).contains("Set sourceLanguage to \"zh\"");
        assertThat(prompt).contains("Set targetLanguage to \"en\"");
        assertThat(prompt).doesNotContain("Detect whether the source text is zh or en");
    }
}

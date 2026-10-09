package com.songzy.dicserver.service;

import com.songzy.dicserver.model.EntryQueryResponse;
import com.songzy.dicserver.model.EntryResultType;
import com.songzy.dicserver.model.TextTranslationResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueryHistorySummaryBuilderTest {

    private final QueryHistorySummaryBuilder builder = new QueryHistorySummaryBuilder();

    @Test
    void buildSummaryShouldUseFirstEnglishWordMeaning() {
        EntryQueryResponse response = new EntryQueryResponse();
        response.setResultType(EntryResultType.ENGLISH_WORD);
        EntryQueryResponse.EnglishWordPayload payload = new EntryQueryResponse.EnglishWordPayload();
        EntryQueryResponse.Definition first = new EntryQueryResponse.Definition();
        first.setMeaning("可见的");
        EntryQueryResponse.Definition second = new EntryQueryResponse.Definition();
        second.setMeaning("看得见的");
        payload.setDefinitions(List.of(first, second));
        response.setEnglishWord(payload);

        assertThat(builder.buildSummary(response)).isEqualTo("可见的");
    }

    @Test
    void buildSummaryShouldUseFirstZhToEnCandidateAndCoreMeaningWhenPresent() {
        EntryQueryResponse response = new EntryQueryResponse();
        response.setResultType(EntryResultType.ZH_TO_EN_TERM);
        EntryQueryResponse.ZhToEnTermPayload payload = new EntryQueryResponse.ZhToEnTermPayload();
        EntryQueryResponse.TermCandidate candidate = new EntryQueryResponse.TermCandidate();
        candidate.setTerm("visible");
        candidate.setCoreMeaning("可见的");
        payload.setCandidates(List.of(candidate));
        response.setZhToEnTerm(payload);

        assertThat(builder.buildSummary(response)).isEqualTo("visible - 可见的");
    }

    @Test
    void buildSummaryShouldUseTranslatedSentenceAndTruncateLongSentence() {
        String longSentence = "This is a very long translated sentence used to verify that sentence summaries "
                + "are truncated to a stable display length for history rows.";
        EntryQueryResponse response = new EntryQueryResponse();
        response.setResultType(EntryResultType.SENTENCE_TRANSLATION);
        EntryQueryResponse.SentenceTranslationPayload payload = new EntryQueryResponse.SentenceTranslationPayload();
        payload.setTranslatedSentence(longSentence);
        response.setSentenceTranslation(payload);

        String summary = builder.buildSummary(response);
        assertThat(summary).hasSizeLessThanOrEqualTo(103);
        assertThat(summary).startsWith("This is a very long translated sentence");
    }

    @Test
    void buildSummaryShouldFallbackToEmptyStringWhenRequiredFieldsAreMissing() {
        EntryQueryResponse englishWordResponse = new EntryQueryResponse();
        englishWordResponse.setResultType(EntryResultType.ENGLISH_WORD);
        englishWordResponse.setEnglishWord(new EntryQueryResponse.EnglishWordPayload());

        EntryQueryResponse zhToEnResponse = new EntryQueryResponse();
        zhToEnResponse.setResultType(EntryResultType.ZH_TO_EN_TERM);
        zhToEnResponse.setZhToEnTerm(new EntryQueryResponse.ZhToEnTermPayload());

        EntryQueryResponse sentenceResponse = new EntryQueryResponse();
        sentenceResponse.setResultType(EntryResultType.SENTENCE_TRANSLATION);
        sentenceResponse.setSentenceTranslation(new EntryQueryResponse.SentenceTranslationPayload());

        assertThat(builder.buildSummary(englishWordResponse)).isEmpty();
        assertThat(builder.buildSummary(zhToEnResponse)).isEmpty();
        assertThat(builder.buildSummary(sentenceResponse)).isEmpty();
    }

    @Test
    void buildTextTranslationSummaryShouldUseTranslatedTextAndTruncateTo100Characters() {
        TextTranslationResponse response = new TextTranslationResponse();
        response.setTranslatedText(" ".repeat(2) + "A".repeat(120) + " ");

        String summary = builder.buildTextTranslationSummary(response);

        assertThat(summary)
                .startsWith("A".repeat(100))
                .endsWith("...")
                .hasSize(103);
    }
}

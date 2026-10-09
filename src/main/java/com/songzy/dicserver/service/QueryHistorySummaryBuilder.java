package com.songzy.dicserver.service;

import com.songzy.dicserver.model.EntryQueryResponse;
import com.songzy.dicserver.model.EntryResultType;
import com.songzy.dicserver.model.TextTranslationResponse;
import org.springframework.stereotype.Component;

@Component
public class QueryHistorySummaryBuilder {

    private static final int SENTENCE_SUMMARY_MAX_LENGTH = 100;

    public String buildSummary(EntryQueryResponse response) {
        if (response == null || response.getResultType() == null) {
            return "";
        }

        return switch (response.getResultType()) {
            case ENGLISH_WORD -> buildEnglishWordSummary(response);
            case ZH_TO_EN_TERM -> buildZhToEnTermSummary(response);
            case SENTENCE_TRANSLATION -> buildSentenceTranslationSummary(response);
            case TEXT_TRANSLATION -> throw new IllegalArgumentException(
                    "Use buildTextTranslationSummary for TEXT_TRANSLATION");
        };
    }

    public String buildTextTranslationSummary(TextTranslationResponse response) {
        if (response == null) {
            return "";
        }
        return truncateSummary(sanitize(response.getTranslatedText()));
    }

    private String buildEnglishWordSummary(EntryQueryResponse response) {
        if (response.getEnglishWord() == null
                || response.getEnglishWord().getDefinitions() == null
                || response.getEnglishWord().getDefinitions().isEmpty()) {
            return "";
        }
        return sanitize(response.getEnglishWord().getDefinitions().getFirst().getMeaning());
    }

    private String buildZhToEnTermSummary(EntryQueryResponse response) {
        if (response.getZhToEnTerm() == null
                || response.getZhToEnTerm().getCandidates() == null
                || response.getZhToEnTerm().getCandidates().isEmpty()) {
            return "";
        }

        EntryQueryResponse.TermCandidate candidate = response.getZhToEnTerm().getCandidates().getFirst();
        String term = sanitize(candidate.getTerm());
        if (term.isEmpty()) {
            return "";
        }

        String coreMeaning = sanitize(candidate.getCoreMeaning());
        if (coreMeaning.isEmpty()) {
            return term;
        }
        return term + " - " + coreMeaning;
    }

    private String buildSentenceTranslationSummary(EntryQueryResponse response) {
        if (response.getSentenceTranslation() == null) {
            return "";
        }

        return truncateSummary(sanitize(response.getSentenceTranslation().getTranslatedSentence()));
    }

    private String truncateSummary(String text) {
        if (text.isEmpty()) {
            return "";
        }
        if (text.length() <= SENTENCE_SUMMARY_MAX_LENGTH) {
            return text;
        }
        return text.substring(0, SENTENCE_SUMMARY_MAX_LENGTH) + "...";
    }

    private String sanitize(String value) {
        return value == null ? "" : value.trim();
    }
}

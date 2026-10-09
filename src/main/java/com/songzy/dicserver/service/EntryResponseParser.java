package com.songzy.dicserver.service;

import com.songzy.dicserver.exception.BusinessException;
import com.songzy.dicserver.model.EntryQueryResponse;
import com.songzy.dicserver.model.EntryResultType;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EntryResponseParser {
    private static final Logger logger = LoggerFactory.getLogger(EntryResponseParser.class);

    public EntryQueryResponse parse(String rawJson, EntryResultType requestedType) {
        rejectTextTranslation(requestedType);

        try {
            String sanitizedRawJson = JsonContentSanitizer.sanitize(rawJson);
            JSONObject root = new JSONObject(sanitizedRawJson);
            EntryResultType parsedType = parseResultType(requireString(root, "resultType"));
            rejectTextTranslation(parsedType);

            if (requestedType != null && parsedType != requestedType) {
                throw new BusinessException("返回结果类型与请求类型不匹配", "LLM_RESPONSE_INVALID");
            }

            EntryQueryResponse response = new EntryQueryResponse();
            response.setQuery(requireString(root, "query"));
            response.setNormalizedQuery(requireString(root, "normalizedQuery"));
            response.setResultType(parsedType);
            response.setSourceLanguage(requireString(root, "sourceLanguage"));
            response.setTargetLanguage(requireString(root, "targetLanguage"));

            JSONObject payload = requireObject(root, "payload");
            switch (parsedType) {
                case ENGLISH_WORD -> response.setEnglishWord(parseEnglishWord(payload));
                case ZH_TO_EN_TERM -> response.setZhToEnTerm(parseZhToEnTerm(payload));
                case SENTENCE_TRANSLATION -> response.setSentenceTranslation(parseSentenceTranslation(payload));
                case TEXT_TRANSLATION -> throw invalidResponse();
            }
            return response;
        } catch (BusinessException ex) {
            logger.warn("Entry response validation failed: code={} raw={}",
                    ex.getCode(), JsonContentSanitizer.abbreviate(rawJson, 400));
            throw ex;
        } catch (Exception ex) {
            logger.warn("Entry response parsing failed: {} raw={}",
                    ex.getMessage(), JsonContentSanitizer.abbreviate(rawJson, 400), ex);
            throw invalidResponse();
        }
    }

    private EntryResultType parseResultType(String value) {
        try {
            return EntryResultType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw invalidResponse();
        }
    }

    private EntryQueryResponse.EnglishWordPayload parseEnglishWord(JSONObject payload) {
        EntryQueryResponse.EnglishWordPayload englishWord = new EntryQueryResponse.EnglishWordPayload();
        englishWord.setWord(requireString(payload, "word"));
        englishWord.setPronunciation(parsePronunciation(requireObject(payload, "pronunciation")));
        englishWord.setDefinitions(parseDefinitions(requireArray(payload, "definitions")));
        englishWord.setExamples(parseExamples(requireArray(payload, "examples")));
        englishWord.setEtymology(requireString(payload, "etymology"));
        englishWord.setExtension(requireString(payload, "extension"));
        englishWord.setHeadwordAudio(parseOptionalHeadwordAudio(payload, "headwordAudio"));
        return englishWord;
    }

    private EntryQueryResponse.ZhToEnTermPayload parseZhToEnTerm(JSONObject payload) {
        EntryQueryResponse.ZhToEnTermPayload zhToEnTerm = new EntryQueryResponse.ZhToEnTermPayload();
        zhToEnTerm.setInput(requireString(payload, "input"));
        zhToEnTerm.setUsageTip(requireString(payload, "usageTip"));
        zhToEnTerm.setCandidates(parseCandidates(requireArray(payload, "candidates")));
        return zhToEnTerm;
    }

    private EntryQueryResponse.SentenceTranslationPayload parseSentenceTranslation(JSONObject payload) {
        EntryQueryResponse.SentenceTranslationPayload sentenceTranslation =
                new EntryQueryResponse.SentenceTranslationPayload();
        sentenceTranslation.setSourceSentence(requireString(payload, "sourceSentence"));
        sentenceTranslation.setTranslatedSentence(requireString(payload, "translatedSentence"));
        sentenceTranslation.setLiteralTranslation(requireString(payload, "literalTranslation"));
        sentenceTranslation.setGrammarBreakdown(requireString(payload, "grammarBreakdown"));
        sentenceTranslation.setKeyPhrases(parseKeyPhrases(requireArray(payload, "keyPhrases")));
        sentenceTranslation.setAlternativeExpressions(parseStringList(requireArray(payload, "alternativeExpressions")));
        sentenceTranslation.setLearningNotes(requireString(payload, "learningNotes"));
        return sentenceTranslation;
    }

    private EntryQueryResponse.Pronunciation parsePronunciation(JSONObject payload) {
        EntryQueryResponse.Pronunciation pronunciation = new EntryQueryResponse.Pronunciation();
        pronunciation.setUk(requireString(payload, "uk"));
        pronunciation.setUs(requireString(payload, "us"));
        return pronunciation;
    }

    private List<EntryQueryResponse.Definition> parseDefinitions(JSONArray definitionsArray) {
        List<EntryQueryResponse.Definition> definitions = new ArrayList<>();
        for (int i = 0; i < definitionsArray.length(); i++) {
            JSONObject definitionObject = definitionsArray.getJSONObject(i);
            EntryQueryResponse.Definition definition = new EntryQueryResponse.Definition();
            definition.setPartOfSpeech(requireString(definitionObject, "partOfSpeech"));
            definition.setMeaning(requireString(definitionObject, "meaning"));
            definition.setSynonyms(parseStringList(requireArray(definitionObject, "synonyms")));
            definitions.add(definition);
        }
        return definitions;
    }

    private List<EntryQueryResponse.Example> parseExamples(JSONArray examplesArray) {
        List<EntryQueryResponse.Example> examples = new ArrayList<>();
        for (int i = 0; i < examplesArray.length(); i++) {
            JSONObject exampleObject = examplesArray.getJSONObject(i);
            EntryQueryResponse.Example example = new EntryQueryResponse.Example();
            example.setSentence(requireString(exampleObject, "sentence"));
            example.setTranslation(requireString(exampleObject, "translation"));
            examples.add(example);
        }
        return examples;
    }

    private List<EntryQueryResponse.TermCandidate> parseCandidates(JSONArray candidatesArray) {
        List<EntryQueryResponse.TermCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < candidatesArray.length(); i++) {
            JSONObject candidateObject = candidatesArray.getJSONObject(i);
            EntryQueryResponse.TermCandidate candidate = new EntryQueryResponse.TermCandidate();
            candidate.setTerm(requireString(candidateObject, "term"));
            candidate.setPronunciation(parsePronunciation(requireObject(candidateObject, "pronunciation")));
            candidate.setPartOfSpeech(requireString(candidateObject, "partOfSpeech"));
            candidate.setCoreMeaning(requireString(candidateObject, "coreMeaning"));
            candidate.setUsageContext(requireString(candidateObject, "usageContext"));
            candidate.setDifference(requireString(candidateObject, "difference"));
            candidate.setExample(requireString(candidateObject, "example"));
            candidate.setExampleTranslation(requireString(candidateObject, "exampleTranslation"));
            candidate.setHeadwordAudio(parseOptionalHeadwordAudio(candidateObject, "headwordAudio"));
            candidates.add(candidate);
        }
        return candidates;
    }

    private List<EntryQueryResponse.KeyPhrase> parseKeyPhrases(JSONArray keyPhrasesArray) {
        List<EntryQueryResponse.KeyPhrase> keyPhrases = new ArrayList<>();
        for (int i = 0; i < keyPhrasesArray.length(); i++) {
            JSONObject keyPhraseObject = keyPhrasesArray.getJSONObject(i);
            EntryQueryResponse.KeyPhrase keyPhrase = new EntryQueryResponse.KeyPhrase();
            keyPhrase.setPhrase(requireString(keyPhraseObject, "phrase"));
            keyPhrase.setExplanation(requireString(keyPhraseObject, "explanation"));
            keyPhrases.add(keyPhrase);
        }
        return keyPhrases;
    }

    private List<String> parseStringList(JSONArray values) {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) {
            items.add(values.getString(i));
        }
        return items;
    }

    private EntryQueryResponse.HeadwordAudio parseOptionalHeadwordAudio(JSONObject payload, String key) {
        if (!payload.has(key) || payload.isNull(key)) {
            return null;
        }

        JSONObject audioObject = payload.getJSONObject(key);
        EntryQueryResponse.HeadwordAudio headwordAudio = new EntryQueryResponse.HeadwordAudio();
        headwordAudio.setAudioKey(requireString(audioObject, "audioKey"));
        headwordAudio.setAudioUrl(requireString(audioObject, "audioUrl"));
        return headwordAudio;
    }

    private String requireString(JSONObject object, String key) {
        if (!object.has(key) || object.isNull(key)) {
            throw invalidResponse();
        }
        return object.getString(key);
    }

    private JSONObject requireObject(JSONObject object, String key) {
        if (!object.has(key) || object.isNull(key)) {
            throw invalidResponse();
        }
        return object.getJSONObject(key);
    }

    private JSONArray requireArray(JSONObject object, String key) {
        if (!object.has(key) || object.isNull(key)) {
            throw invalidResponse();
        }
        return object.getJSONArray(key);
    }

    private BusinessException invalidResponse() {
        return new BusinessException("大模型返回内容格式无效", "LLM_RESPONSE_INVALID");
    }

    private void rejectTextTranslation(EntryResultType resultType) {
        if (resultType == EntryResultType.TEXT_TRANSLATION) {
            throw new IllegalArgumentException("EntryResponseParser does not support TEXT_TRANSLATION");
        }
    }
}

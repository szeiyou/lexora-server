package com.songzy.dicserver.service;

import com.songzy.dicserver.exception.BusinessException;
import com.songzy.dicserver.model.TextTranslationResponse;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TextTranslationResponseParser {
    private static final Logger logger = LoggerFactory.getLogger(TextTranslationResponseParser.class);

    public TextTranslationResponse parse(String rawJson) {
        try {
            String sanitizedRawJson = JsonContentSanitizer.sanitize(rawJson);
            JSONObject root = new JSONObject(sanitizedRawJson);

            TextTranslationResponse response = new TextTranslationResponse();
            response.setText(requireString(root, "text"));
            response.setNormalizedText(requireString(root, "normalizedText"));
            response.setSourceLanguage(requireString(root, "sourceLanguage"));
            response.setTargetLanguage(requireString(root, "targetLanguage"));
            response.setTranslatedText(requireString(root, "translatedText"));
            response.setSegments(parseSegments(requireArray(root, "segments")));
            response.setKeyPhrases(parseKeyPhrases(requireArray(root, "keyPhrases")));
            response.setNotes(requireString(root, "notes"));
            return response;
        } catch (BusinessException ex) {
            logger.warn("Text translation response validation failed: code={} raw={}",
                    ex.getCode(), JsonContentSanitizer.abbreviate(rawJson, 400));
            throw ex;
        } catch (Exception ex) {
            logger.warn("Text translation response parsing failed: {} raw={}",
                    ex.getMessage(), JsonContentSanitizer.abbreviate(rawJson, 400), ex);
            throw invalidResponse();
        }
    }

    private List<TextTranslationResponse.Segment> parseSegments(JSONArray array) {
        List<TextTranslationResponse.Segment> segments = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject segmentObject = array.getJSONObject(i);
            TextTranslationResponse.Segment segment = new TextTranslationResponse.Segment();
            segment.setSourceText(requireString(segmentObject, "sourceText"));
            segment.setTranslatedText(requireString(segmentObject, "translatedText"));
            segments.add(segment);
        }
        return segments;
    }

    private List<TextTranslationResponse.KeyPhrase> parseKeyPhrases(JSONArray array) {
        List<TextTranslationResponse.KeyPhrase> keyPhrases = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject keyPhraseObject = array.getJSONObject(i);
            TextTranslationResponse.KeyPhrase keyPhrase = new TextTranslationResponse.KeyPhrase();
            keyPhrase.setSourceText(requireString(keyPhraseObject, "sourceText"));
            keyPhrase.setTranslatedText(requireString(keyPhraseObject, "translatedText"));
            keyPhrase.setNote(requireString(keyPhraseObject, "note"));
            keyPhrases.add(keyPhrase);
        }
        return keyPhrases;
    }

    private String requireString(JSONObject object, String key) {
        if (!object.has(key) || object.isNull(key)) {
            throw invalidResponse();
        }
        return object.getString(key);
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
}

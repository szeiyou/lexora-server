package com.songzy.dicserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class TextTranslationRequest {

    @NotBlank
    private String text;

    @Pattern(regexp = "auto|zh|en")
    private String sourceLanguage;

    @NotBlank
    @Pattern(regexp = "zh|en")
    private String targetLanguage;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getSourceLanguage() {
        return sourceLanguage;
    }

    public void setSourceLanguage(String sourceLanguage) {
        this.sourceLanguage = sourceLanguage;
    }

    public String getTargetLanguage() {
        return targetLanguage;
    }

    public void setTargetLanguage(String targetLanguage) {
        this.targetLanguage = targetLanguage;
    }

    public String trimmedText() {
        return text == null ? null : text.trim();
    }

    public String resolvedSourceLanguage() {
        return sourceLanguage == null ? "auto" : sourceLanguage;
    }
}

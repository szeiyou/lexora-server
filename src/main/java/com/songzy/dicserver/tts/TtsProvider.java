package com.songzy.dicserver.tts;

import com.songzy.dicserver.model.AudioType;

/**
 * TTS provider interface for text-to-speech synthesis.
 * Implementations can support different TTS backends (Qwen3, Tencent Cloud, etc.).
 */
public interface TtsProvider {

    /**
     * Get the unique name identifier for this provider.
     * @return provider name (e.g., "qwen3", "tencent")
     */
    String getName();

    /**
     * Synthesize text to audio bytes.
     * @param text the text to synthesize
     * @param audioType the logical audio type for this synthesis request
     * @return audio data as byte array, or null on failure
     */
    byte[] synthesize(String text, AudioType audioType);

    default boolean loadModels() {
        return true;
    }

    default boolean unloadModels() {
        return true;
    }
}

package com.songzy.dicserver.service;

import com.songzy.dicserver.model.AudioType;
import com.songzy.dicserver.model.WordResponse;
import com.songzy.dicserver.tts.TtsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * TTS service coordinator that manages multiple TTS providers
 * and delegates synthesis to the active provider.
 */
@Service
public class TtsService {
    private static final Logger logger = LoggerFactory.getLogger(TtsService.class);

    private final Map<String, TtsProvider> providers;
    private final AtomicReference<String> activeProviderName;

    public TtsService(List<TtsProvider> providerList,
                      @Value("${tts.provider:qwen3}") String defaultProvider) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(TtsProvider::getName, p -> p));
        this.activeProviderName = new AtomicReference<>(defaultProvider);
        logger.info("TTS service initialized with providers: {}, active: {}",
                providers.keySet(), defaultProvider);
    }

    public String buildReadingText(String word, WordResponse response) {
        StringBuilder text = new StringBuilder();

        text.append(word).append("。");
        text.append(word.toUpperCase().chars()
                        .mapToObj(ch -> String.valueOf((char) ch))
                        .collect(Collectors.joining(", ")))
                .append("。");

        for (WordResponse.Definition def : response.getDefinitions()) {
            text.append(def.getMeaning()).append("。");
        }

        if (response.getExamples() != null) {
            for (WordResponse.Example example : response.getExamples()) {
                text.append(example.getSentence()).append("。")
                        .append(example.getTranslation()).append("。");
            }
        }

        if (response.getExtension() != null && !response.getExtension().isEmpty()) {
            text.append("扩展信息：").append(response.getExtension());
        }

        return text.toString();
    }

    /**
     * Synthesize text using the currently active provider.
     */
    public byte[] synthesize(String text, AudioType audioType) {
        String name = activeProviderName.get();
        TtsProvider provider = providers.get(name);
        if (provider == null) {
            logger.error("TTS provider '{}' not found, available: {}", name, providers.keySet());
            return null;
        }
        logger.debug("Delegating TTS synthesis to provider: {}", name);
        return provider.synthesize(text, audioType);
    }

    /**
     * Switch the active TTS provider at runtime.
     * @param name the provider name to activate
     * @return true if switch succeeded, false if provider not found
     */
    public boolean switchProvider(String name) {
        if (!providers.containsKey(name)) {
            logger.warn("Cannot switch to unknown TTS provider: {}, available: {}", name, providers.keySet());
            return false;
        }
        String old = activeProviderName.getAndSet(name);
        logger.info("Switched TTS provider: {} -> {}", old, name);
        return true;
    }

    /**
     * Get the name of the currently active provider.
     */
    public String getActiveProvider() {
        return activeProviderName.get();
    }

    /**
     * Get list of available provider names.
     */
    public List<String> getAvailableProviders() {
        return List.copyOf(providers.keySet());
    }

    public boolean loadActiveProviderModels() {
        TtsProvider provider = providers.get(activeProviderName.get());
        if (provider == null) {
            logger.error("Cannot load models because active provider '{}' was not found", activeProviderName.get());
            return false;
        }
        return provider.loadModels();
    }

    public boolean unloadActiveProviderModels() {
        TtsProvider provider = providers.get(activeProviderName.get());
        if (provider == null) {
            logger.error("Cannot unload models because active provider '{}' was not found", activeProviderName.get());
            return false;
        }
        return provider.unloadModels();
    }
}

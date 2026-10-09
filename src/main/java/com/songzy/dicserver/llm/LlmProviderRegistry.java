package com.songzy.dicserver.llm;

import com.songzy.dicserver.config.LlmProperties;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class LlmProviderRegistry {

    private static final Set<String> SUPPORTED_PROTOCOLS = Set.of("openai", "anthropic");

    private final List<LlmProviderConfig> orderedProviders;

    public LlmProviderRegistry(LlmProperties properties) {
        List<LlmProperties.Provider> providers = properties == null ? null : properties.getProviders();
        if (providers == null || providers.isEmpty()) {
            throw new IllegalStateException("llm.providers must not be empty");
        }

        Set<String> names = new HashSet<>();
        for (LlmProperties.Provider provider : providers) {
            requireText(provider.getName(), "provider.name");
            if (!names.add(provider.getName())) {
                throw new IllegalStateException("duplicate provider name: " + provider.getName());
            }

            if (!provider.isEnabled()) {
                continue;
            }

            requireText(provider.getProtocol(), "provider.protocol");
            if (!SUPPORTED_PROTOCOLS.contains(provider.getProtocol())) {
                throw new IllegalStateException("unsupported protocol: " + provider.getProtocol());
            }

            requireText(provider.getBaseUrl(), "provider.baseUrl");
            requireText(provider.getApiKey(), "provider.apiKey");
            requireText(provider.getModel(), "provider.model");
        }

        List<LlmProviderConfig> enabledProviders = providers.stream()
                .filter(LlmProperties.Provider::isEnabled)
                .map(this::toRuntimeConfig)
                .sorted(Comparator.comparingInt(LlmProviderConfig::priority).thenComparing(LlmProviderConfig::name))
                .toList();

        if (enabledProviders.isEmpty()) {
            throw new IllegalStateException("at least one enabled provider is required");
        }
        this.orderedProviders = enabledProviders;
    }

    public List<LlmProviderConfig> getOrderedProviders() {
        return orderedProviders;
    }

    private LlmProviderConfig toRuntimeConfig(LlmProperties.Provider provider) {
        LlmProperties.ProviderOptions sourceOptions = provider.getOptions() == null
                ? new LlmProperties.ProviderOptions()
                : provider.getOptions();
        Map<String, String> headers = sourceOptions.getHeaders() == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(sourceOptions.getHeaders()));
        LlmProviderConfig.ProviderOptions options = new LlmProviderConfig.ProviderOptions(
                sourceOptions.getTemperature(),
                sourceOptions.getMaxTokens(),
                sourceOptions.getTopP(),
                headers
        );
        return new LlmProviderConfig(
                provider.getName(),
                provider.getProtocol(),
                provider.getBaseUrl(),
                provider.getApiKey(),
                provider.getModel(),
                provider.getPriority(),
                options
        );
    }

    private static void requireText(String value, String fieldName) {
        if (Objects.isNull(value) || value.isBlank()) {
            throw new IllegalStateException(fieldName + " must not be blank");
        }
    }
}

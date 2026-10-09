package com.songzy.dicserver.llm;

import java.math.BigDecimal;
import java.util.Map;

public record LlmProviderConfig(
        String name,
        String protocol,
        String baseUrl,
        String apiKey,
        String model,
        int priority,
        ProviderOptions options
) {
    public record ProviderOptions(
            BigDecimal temperature,
            Integer maxTokens,
            BigDecimal topP,
            Map<String, String> headers
    ) {
    }
}

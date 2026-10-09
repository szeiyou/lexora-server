package com.songzy.dicserver.llm;

import com.songzy.dicserver.config.LlmProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmProviderRegistryTest {

    @Test
    void shouldRejectEmptyProviderList() {
        LlmProperties properties = new LlmProperties();
        properties.setProviders(List.of());

        assertThatThrownBy(() -> new LlmProviderRegistry(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("llm.providers");
    }

    @Test
    void shouldRejectWhenNoEnabledProvider() {
        LlmProperties properties = new LlmProperties();
        properties.setProviders(List.of(
                provider("primary", "openai", false, 10, "https://example.com/v1/chat/completions", "k", "m1")
        ));

        assertThatThrownBy(() -> new LlmProviderRegistry(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("enabled");
    }

    @Test
    void shouldRejectDuplicateProviderNames() {
        LlmProperties properties = new LlmProperties();
        properties.setProviders(List.of(
                provider("dup", "openai", true, 10, "https://example.com/v1/chat/completions", "k1", "m1"),
                provider("dup", "anthropic", true, 20, "https://example.com/v1/messages", "k2", "m2")
        ));

        assertThatThrownBy(() -> new LlmProviderRegistry(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void shouldRejectDuplicateProviderNamesEvenWhenOneIsDisabled() {
        LlmProperties properties = new LlmProperties();
        properties.setProviders(List.of(
                provider("dup", "openai", true, 10, "https://example.com/v1/chat/completions", "k1", "m1"),
                provider("dup", "openai", false, 20, "", "", "")
        ));

        assertThatThrownBy(() -> new LlmProviderRegistry(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void shouldRejectBlankNameEvenWhenProviderDisabled() {
        LlmProperties properties = new LlmProperties();
        properties.setProviders(List.of(
                provider("primary", "openai", true, 10, "https://example.com/v1/chat/completions", "k1", "m1"),
                provider(" ", "openai", false, 20, "", "", "")
        ));

        assertThatThrownBy(() -> new LlmProviderRegistry(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("provider.name");
    }

    @Test
    void shouldRejectUnsupportedProtocol() {
        LlmProperties properties = new LlmProperties();
        properties.setProviders(List.of(
                provider("bad", "azure-openai", true, 10, "https://example.com/v1/chat/completions", "k", "m")
        ));

        assertThatThrownBy(() -> new LlmProviderRegistry(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("protocol");
    }

    @Test
    void shouldRejectMissingBaseUrl() {
        LlmProperties properties = new LlmProperties();
        properties.setProviders(List.of(
                provider("p1", "openai", true, 10, " ", "k", "m")
        ));

        assertThatThrownBy(() -> new LlmProviderRegistry(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("baseUrl");
    }

    @Test
    void shouldRejectMissingApiKey() {
        LlmProperties properties = new LlmProperties();
        properties.setProviders(List.of(
                provider("p1", "openai", true, 10, "https://example.com/v1/chat/completions", "", "m")
        ));

        assertThatThrownBy(() -> new LlmProviderRegistry(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("apiKey");
    }

    @Test
    void shouldRejectMissingModel() {
        LlmProperties properties = new LlmProperties();
        properties.setProviders(List.of(
                provider("p1", "openai", true, 10, "https://example.com/v1/chat/completions", "k", "")
        ));

        assertThatThrownBy(() -> new LlmProviderRegistry(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("model");
    }

    @Test
    void shouldAllowMissingFieldsForDisabledProvider() {
        LlmProperties properties = new LlmProperties();
        properties.setProviders(List.of(
                provider("enabled", "openai", true, 10, "https://example.com/v1/chat/completions", "k1", "m1"),
                provider("disabled", "", false, 20, "", "", "")
        ));

        LlmProviderRegistry registry = new LlmProviderRegistry(properties);

        assertThat(registry.getOrderedProviders())
                .extracting(LlmProviderConfig::name)
                .containsExactly("enabled");
    }

    @Test
    void shouldExposeEnabledProvidersOrderedByPriorityThenName() {
        LlmProperties properties = new LlmProperties();
        properties.setProviders(List.of(
                provider("zeta", "openai", true, 20, "https://zeta.example.com/v1/chat/completions", "k1", "m1"),
                provider("disabled", "openai", false, 1, "https://disabled.example.com/v1/chat/completions", "k2", "m2"),
                provider("alpha", "anthropic", true, 20, "https://alpha.example.com/v1/messages", "k3", "m3"),
                provider("beta", "openai", true, 10, "https://beta.example.com/v1/chat/completions", "k4", "m4")
        ));

        LlmProviderRegistry registry = new LlmProviderRegistry(properties);

        assertThat(registry.getOrderedProviders())
                .extracting(LlmProviderConfig::name)
                .containsExactly("beta", "alpha", "zeta");
    }

    @Test
    void shouldReturnImmutableRuntimeView() {
        LlmProperties.Provider provider = provider(
                "immutable", "openai", true, 10, "https://example.com/v1/chat/completions", "k", "m"
        );
        provider.getOptions().setHeaders(new java.util.LinkedHashMap<>(Map.of("x-extra", "1")));
        LlmProperties properties = new LlmProperties();
        properties.setProviders(new ArrayList<>(List.of(provider)));

        LlmProviderRegistry registry = new LlmProviderRegistry(properties);
        List<LlmProviderConfig> providers = registry.getOrderedProviders();

        assertThatThrownBy(() -> providers.add(providers.getFirst()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> providers.getFirst().options().headers().put("x-new", "2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldKeepConfiguredOptionsInRuntimeConfig() {
        LlmProperties.Provider provider = provider(
                "with-options", "openai", true, 10, "https://example.com/v1/chat/completions", "k", "m"
        );
        provider.getOptions().setTemperature(new BigDecimal("0.33"));
        provider.getOptions().setTopP(new BigDecimal("0.77"));
        provider.getOptions().setMaxTokens(2048);
        provider.getOptions().setHeaders(new java.util.LinkedHashMap<>(Map.of(
                "x-extra-1", "v1",
                "x-extra-2", "v2"
        )));

        LlmProperties properties = new LlmProperties();
        properties.setProviders(List.of(provider));
        LlmProviderRegistry registry = new LlmProviderRegistry(properties);

        LlmProviderConfig runtimeConfig = registry.getOrderedProviders().getFirst();
        assertThat(runtimeConfig.options().temperature()).isEqualByComparingTo("0.33");
        assertThat(runtimeConfig.options().topP()).isEqualByComparingTo("0.77");
        assertThat(runtimeConfig.options().maxTokens()).isEqualTo(2048);
        assertThat(runtimeConfig.options().headers())
                .containsEntry("x-extra-1", "v1")
                .containsEntry("x-extra-2", "v2");
    }

    private static LlmProperties.Provider provider(String name,
                                                   String protocol,
                                                   boolean enabled,
                                                   int priority,
                                                   String baseUrl,
                                                   String apiKey,
                                                   String model) {
        LlmProperties.Provider provider = new LlmProperties.Provider();
        provider.setName(name);
        provider.setProtocol(protocol);
        provider.setEnabled(enabled);
        provider.setPriority(priority);
        provider.setBaseUrl(baseUrl);
        provider.setApiKey(apiKey);
        provider.setModel(model);

        LlmProperties.ProviderOptions options = new LlmProperties.ProviderOptions();
        options.setTemperature(new BigDecimal("0.2"));
        options.setTopP(new BigDecimal("0.9"));
        options.setMaxTokens(1024);
        provider.setOptions(options);
        return provider;
    }
}

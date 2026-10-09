package com.songzy.dicserver.service;

import com.songzy.dicserver.exception.BusinessException;
import com.songzy.dicserver.llm.LlmProtocolAdapter;
import com.songzy.dicserver.llm.LlmProviderConfig;
import com.songzy.dicserver.llm.LlmProviderRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmServiceTest {

    private static final String RAW_INNER_CONTENT = "{\"query\":\"苹果\"}";

    @Mock
    private LlmProviderRegistry registry;

    @Mock
    private LlmProtocolAdapter openAiAdapter;

    @Mock
    private LlmProtocolAdapter anthropicAdapter;

    private LlmService llmService;

    @BeforeEach
    void setUp() {
        when(openAiAdapter.protocol()).thenReturn("openai");
        when(anthropicAdapter.protocol()).thenReturn("anthropic");
        llmService = new LlmService(registry, List.of(openAiAdapter, anthropicAdapter));
    }

    @Test
    void queryRawJsonShouldUseOrderedProvidersAndSanitizeAtServiceLayer() {
        LlmProviderConfig firstProvider = provider("deepseek-main", "openai", 10);
        LlmProviderConfig secondProvider = provider("qwen-backup", "openai", 20);
        when(registry.getOrderedProviders()).thenReturn(List.of(firstProvider, secondProvider));
        when(openAiAdapter.queryRawJson(firstProvider, "苹果", "prompt"))
                .thenThrow(new BusinessException("大模型接口调用失败", "LLM_API_ERROR"));
        when(openAiAdapter.queryRawJson(secondProvider, "苹果", "prompt"))
                .thenReturn("```json\n" + RAW_INNER_CONTENT + "\n```");

        String result = llmService.queryRawJson("苹果", "prompt");

        assertThat(result).isEqualTo(RAW_INNER_CONTENT);
        verify(openAiAdapter).queryRawJson(firstProvider, "苹果", "prompt");
        verify(openAiAdapter).queryRawJson(secondProvider, "苹果", "prompt");
        verify(anthropicAdapter, never()).queryRawJson(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void queryRawJsonShouldSwitchAdaptersByProviderProtocol() {
        LlmProviderConfig openAiProvider = provider("deepseek-main", "openai", 10);
        LlmProviderConfig anthropicProvider = provider("claude-gateway", "anthropic", 20);
        when(registry.getOrderedProviders()).thenReturn(List.of(openAiProvider, anthropicProvider));
        when(openAiAdapter.queryRawJson(openAiProvider, "苹果", "prompt"))
                .thenThrow(new BusinessException("大模型接口调用失败", "LLM_API_ERROR"));
        when(anthropicAdapter.queryRawJson(anthropicProvider, "苹果", "prompt"))
                .thenReturn(RAW_INNER_CONTENT);

        String result = llmService.queryRawJson("苹果", "prompt");

        assertThat(result).isEqualTo(RAW_INNER_CONTENT);
        verify(openAiAdapter).queryRawJson(openAiProvider, "苹果", "prompt");
        verify(anthropicAdapter).queryRawJson(anthropicProvider, "苹果", "prompt");
    }

    @Test
    void queryRawJsonShouldTreatMissingProtocolAdapterAsFallbackFailure() {
        LlmProviderConfig missingAdapterProvider = provider("custom-gateway", "custom", 10);
        LlmProviderConfig backupProvider = provider("deepseek-main", "openai", 20);
        when(registry.getOrderedProviders()).thenReturn(List.of(missingAdapterProvider, backupProvider));
        when(openAiAdapter.queryRawJson(backupProvider, "苹果", "prompt"))
                .thenReturn(RAW_INNER_CONTENT);

        String result = llmService.queryRawJson("苹果", "prompt");

        assertThat(result).isEqualTo(RAW_INNER_CONTENT);
        verify(openAiAdapter).queryRawJson(backupProvider, "苹果", "prompt");
        verify(anthropicAdapter, never()).queryRawJson(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void queryRawJsonShouldThrowAggregatedFailureWhenAllProvidersFail() {
        LlmProviderConfig firstProvider = provider("deepseek-main", "openai", 10);
        LlmProviderConfig secondProvider = provider("claude-gateway", "anthropic", 20);
        when(registry.getOrderedProviders()).thenReturn(List.of(firstProvider, secondProvider));
        when(openAiAdapter.queryRawJson(firstProvider, "苹果", "prompt"))
                .thenThrow(new BusinessException("大模型接口调用失败", "LLM_API_ERROR"));
        when(anthropicAdapter.queryRawJson(secondProvider, "苹果", "prompt"))
                .thenThrow(new BusinessException("大模型返回空响应", "LLM_RESPONSE_EMPTY"));

        assertThatThrownBy(() -> llmService.queryRawJson("苹果", "prompt"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("ALL_LLM_PROVIDERS_FAILED");
                    assertThat(ex.getMessage()).contains("deepseek-main: 大模型接口调用失败");
                    assertThat(ex.getMessage()).contains("claude-gateway: 大模型返回空响应");
                });
    }

    private static LlmProviderConfig provider(String name, String protocol, int priority) {
        return new LlmProviderConfig(
                name,
                protocol,
                "https://example.com/" + name,
                "api-key-" + name,
                "model-" + name,
                priority,
                new LlmProviderConfig.ProviderOptions(null, null, null, Map.of())
        );
    }
}

package com.songzy.dicserver.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songzy.dicserver.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiChatCompletionsAdapterTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private OpenAiChatCompletionsAdapter adapter;

    @Test
    void protocolShouldReturnOpenAi() {
        assertThat(adapter.protocol()).isEqualTo("openai");
    }

    @Test
    void queryRawJsonShouldBuildRequestAndExtractContent() throws Exception {
        LlmProviderConfig provider = providerWithOptionsAndHeaders();
        when(restTemplate.exchange(eq(provider.baseUrl()), eq(HttpMethod.POST), org.mockito.ArgumentMatchers.any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"choices\":[{\"message\":{\"content\":\"{\\\"query\\\":\\\"apple\\\"}\"}}]}"));

        String result = adapter.queryRawJson(provider, "apple", "system prompt");

        assertThat(result).isEqualTo("{\"query\":\"apple\"}");

        ArgumentCaptor<HttpEntity<String>> entityCaptor = ArgumentCaptor.forClass((Class) HttpEntity.class);
        verify(restTemplate).exchange(eq(provider.baseUrl()), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));
        HttpEntity<String> requestEntity = entityCaptor.getValue();
        HttpHeaders headers = requestEntity.getHeaders();
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer test-key");
        assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(headers.getFirst("X-Trace-Id")).isEqualTo("trace-123");

        JsonNode body = OBJECT_MAPPER.readTree(requestEntity.getBody());
        assertThat(body.path("model").asText()).isEqualTo("gpt-4o-mini");
        assertThat(body.path("temperature").decimalValue()).isEqualByComparingTo("0.25");
        assertThat(body.path("top_p").decimalValue()).isEqualByComparingTo("0.9");
        assertThat(body.path("max_tokens").asInt()).isEqualTo(1024);

        JsonNode messages = body.path("messages");
        assertThat(messages.isArray()).isTrue();
        assertThat(messages.size()).isEqualTo(2);
        assertThat(messages.get(0).path("role").asText()).isEqualTo("system");
        assertThat(messages.get(0).path("content").asText()).isEqualTo("system prompt");
        assertThat(messages.get(1).path("role").asText()).isEqualTo("user");
        assertThat(messages.get(1).path("content").asText()).isEqualTo("apple");
    }

    @Test
    void queryRawJsonShouldOmitOptionalFieldsWhenOptionsAbsent() throws Exception {
        LlmProviderConfig provider = new LlmProviderConfig(
                "openai-primary",
                "openai",
                "https://example.com/v1/chat/completions",
                "test-key",
                "gpt-4o-mini",
                10,
                null
        );
        when(restTemplate.exchange(eq(provider.baseUrl()), eq(HttpMethod.POST), org.mockito.ArgumentMatchers.any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}"));

        adapter.queryRawJson(provider, "apple", "system prompt");

        ArgumentCaptor<HttpEntity<String>> entityCaptor = ArgumentCaptor.forClass((Class) HttpEntity.class);
        verify(restTemplate).exchange(eq(provider.baseUrl()), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));
        JsonNode body = OBJECT_MAPPER.readTree(entityCaptor.getValue().getBody());
        assertThat(body.path("temperature").isMissingNode()).isTrue();
        assertThat(body.path("top_p").isMissingNode()).isTrue();
        assertThat(body.path("max_tokens").isMissingNode()).isTrue();
    }

    @Test
    void queryRawJsonShouldThrowBusinessExceptionWhenHttpFails() {
        LlmProviderConfig provider = providerWithOptionsAndHeaders();
        when(restTemplate.exchange(eq(provider.baseUrl()), eq(HttpMethod.POST), org.mockito.ArgumentMatchers.any(), eq(String.class)))
                .thenThrow(new RestClientException("boom"));

        assertThatThrownBy(() -> adapter.queryRawJson(provider, "apple", "system prompt"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("LLM_API_ERROR");
    }

    @Test
    void queryRawJsonShouldThrowBusinessExceptionWhenResponseEnvelopeInvalid() {
        LlmProviderConfig provider = providerWithOptionsAndHeaders();
        when(restTemplate.exchange(eq(provider.baseUrl()), eq(HttpMethod.POST), org.mockito.ArgumentMatchers.any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"choices\":[]}"));

        assertThatThrownBy(() -> adapter.queryRawJson(provider, "apple", "system prompt"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("LLM_RESPONSE_INVALID");
    }

    @Test
    void queryRawJsonShouldThrowBusinessExceptionWhenResponseBodyEmpty() {
        LlmProviderConfig provider = providerWithOptionsAndHeaders();
        when(restTemplate.exchange(eq(provider.baseUrl()), eq(HttpMethod.POST), org.mockito.ArgumentMatchers.any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(""));

        assertThatThrownBy(() -> adapter.queryRawJson(provider, "apple", "system prompt"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("LLM_RESPONSE_EMPTY");
    }

    private static LlmProviderConfig providerWithOptionsAndHeaders() {
        return new LlmProviderConfig(
                "openai-primary",
                "openai",
                "https://example.com/v1/chat/completions",
                "test-key",
                "gpt-4o-mini",
                10,
                new LlmProviderConfig.ProviderOptions(
                        new BigDecimal("0.25"),
                        1024,
                        new BigDecimal("0.9"),
                        Map.of("X-Trace-Id", "trace-123")
                )
        );
    }
}

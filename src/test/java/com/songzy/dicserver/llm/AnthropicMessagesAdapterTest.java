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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnthropicMessagesAdapterTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private AnthropicMessagesAdapter adapter;

    @Test
    void protocolShouldReturnAnthropic() {
        assertThat(adapter.protocol()).isEqualTo("anthropic");
    }

    @Test
    void queryRawJsonShouldBuildRequestAndExtractConcatenatedTextBlocks() throws Exception {
        LlmProviderConfig provider = providerWithOptionsAndHeaders();
        when(restTemplate.exchange(eq(provider.baseUrl()), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"query\\\":\"},{\"type\":\"tool_use\",\"name\":\"ignored\"},{\"type\":\"text\",\"text\":\"\\\"apple\\\"}\"}]}"));

        String result = adapter.queryRawJson(provider, "apple", "system prompt");

        assertThat(result).isEqualTo("{\"query\":\"apple\"}");

        ArgumentCaptor<HttpEntity<String>> entityCaptor = ArgumentCaptor.forClass((Class) HttpEntity.class);
        verify(restTemplate).exchange(eq(provider.baseUrl()), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));
        HttpEntity<String> requestEntity = entityCaptor.getValue();
        HttpHeaders headers = requestEntity.getHeaders();
        assertThat(headers.getFirst("x-api-key")).isEqualTo("test-key");
        assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(headers.getFirst("anthropic-version")).isEqualTo("2023-06-01");
        assertThat(headers.getFirst("X-Trace-Id")).isEqualTo("trace-123");

        JsonNode body = OBJECT_MAPPER.readTree(requestEntity.getBody());
        assertThat(body.path("model").asText()).isEqualTo("claude-3-5-sonnet-20241022");
        assertThat(body.path("system").asText()).isEqualTo("system prompt");
        assertThat(body.path("temperature").decimalValue()).isEqualByComparingTo("0.25");
        assertThat(body.path("top_p").decimalValue()).isEqualByComparingTo("0.9");
        assertThat(body.path("max_tokens").asInt()).isEqualTo(1024);

        JsonNode messages = body.path("messages");
        assertThat(messages.isArray()).isTrue();
        assertThat(messages.size()).isEqualTo(1);
        assertThat(messages.get(0).path("role").asText()).isEqualTo("user");
        assertThat(messages.get(0).path("content").asText()).isEqualTo("apple");
    }

    @Test
    void queryRawJsonShouldUseDefaultsAndCustomAnthropicVersionWhenOptionsPartial() throws Exception {
        LlmProviderConfig provider = new LlmProviderConfig(
                "anthropic-primary",
                "anthropic",
                "https://example.com/v1/messages",
                "test-key",
                "claude-3-5-sonnet-20241022",
                10,
                new LlmProviderConfig.ProviderOptions(
                        null,
                        null,
                        null,
                        Map.of("anthropic-version", "2023-06-15")
                )
        );
        when(restTemplate.exchange(eq(provider.baseUrl()), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}"));

        adapter.queryRawJson(provider, "apple", "system prompt");

        ArgumentCaptor<HttpEntity<String>> entityCaptor = ArgumentCaptor.forClass((Class) HttpEntity.class);
        verify(restTemplate).exchange(eq(provider.baseUrl()), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));
        HttpEntity<String> requestEntity = entityCaptor.getValue();
        assertThat(requestEntity.getHeaders().getFirst("anthropic-version")).isEqualTo("2023-06-15");

        JsonNode body = OBJECT_MAPPER.readTree(requestEntity.getBody());
        assertThat(body.path("max_tokens").asInt()).isEqualTo(4000);
        assertThat(body.path("temperature").isMissingNode()).isTrue();
        assertThat(body.path("top_p").isMissingNode()).isTrue();
    }

    @Test
    void queryRawJsonShouldNotAllowOverridingMandatoryHeaders() {
        LlmProviderConfig provider = new LlmProviderConfig(
                "anthropic-primary",
                "anthropic",
                "https://example.com/v1/messages",
                "test-key",
                "claude-3-5-sonnet-20241022",
                10,
                new LlmProviderConfig.ProviderOptions(
                        null,
                        null,
                        null,
                        Map.of(
                                "x-api-key", "malicious-key",
                                "Content-Type", "text/plain",
                                "anthropic-version", "2023-06-15",
                                "X-Trace-Id", "trace-123"
                        )
                )
        );
        when(restTemplate.exchange(eq(provider.baseUrl()), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}"));

        adapter.queryRawJson(provider, "apple", "system prompt");

        ArgumentCaptor<HttpEntity<String>> entityCaptor = ArgumentCaptor.forClass((Class) HttpEntity.class);
        verify(restTemplate).exchange(eq(provider.baseUrl()), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));
        HttpHeaders headers = entityCaptor.getValue().getHeaders();
        assertThat(headers.getFirst("x-api-key")).isEqualTo("test-key");
        assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(headers.getFirst("anthropic-version")).isEqualTo("2023-06-15");
        assertThat(headers.getFirst("X-Trace-Id")).isEqualTo("trace-123");
    }

    @Test
    void queryRawJsonShouldThrowBusinessExceptionWhenHttpFails() {
        LlmProviderConfig provider = providerWithOptionsAndHeaders();
        when(restTemplate.exchange(eq(provider.baseUrl()), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenThrow(new RestClientException("boom"));

        assertThatThrownBy(() -> adapter.queryRawJson(provider, "apple", "system prompt"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("LLM_API_ERROR");
    }

    @Test
    void queryRawJsonShouldThrowBusinessExceptionWhenResponseBodyEmpty() {
        LlmProviderConfig provider = providerWithOptionsAndHeaders();
        when(restTemplate.exchange(eq(provider.baseUrl()), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(""));

        assertThatThrownBy(() -> adapter.queryRawJson(provider, "apple", "system prompt"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("LLM_RESPONSE_EMPTY");
    }

    @Test
    void queryRawJsonShouldThrowBusinessExceptionWhenResponseEnvelopeInvalid() {
        LlmProviderConfig provider = providerWithOptionsAndHeaders();
        when(restTemplate.exchange(eq(provider.baseUrl()), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"content\":[]}"));

        assertThatThrownBy(() -> adapter.queryRawJson(provider, "apple", "system prompt"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("LLM_RESPONSE_INVALID");
    }

    private static LlmProviderConfig providerWithOptionsAndHeaders() {
        return new LlmProviderConfig(
                "anthropic-primary",
                "anthropic",
                "https://example.com/v1/messages",
                "test-key",
                "claude-3-5-sonnet-20241022",
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

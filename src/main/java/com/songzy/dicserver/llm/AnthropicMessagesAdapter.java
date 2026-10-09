package com.songzy.dicserver.llm;

import com.songzy.dicserver.exception.BusinessException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class AnthropicMessagesAdapter implements LlmProtocolAdapter {
    private static final Logger logger = LoggerFactory.getLogger(AnthropicMessagesAdapter.class);
    private static final int DEFAULT_MAX_TOKENS = 4000;
    private static final String DEFAULT_ANTHROPIC_VERSION = "2023-06-01";

    private final RestTemplate restTemplate;

    public AnthropicMessagesAdapter(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String protocol() {
        return "anthropic";
    }

    @Override
    public String queryRawJson(LlmProviderConfig provider, String userInput, String systemPrompt) {
        JSONObject requestBody = buildRequestBody(provider, userInput, systemPrompt);
        HttpHeaders headers = buildHeaders(provider);
        HttpEntity<String> entity = new HttpEntity<>(requestBody.toString(), headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    provider.baseUrl(), HttpMethod.POST, entity, String.class
            );
            return extractContent(response.getBody());
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            logger.error("调用Anthropic接口失败: {}", e.getMessage(), e);
            throw new BusinessException("大模型接口调用失败", "LLM_API_ERROR");
        }
    }

    private JSONObject buildRequestBody(LlmProviderConfig provider, String userInput, String systemPrompt) {
        JSONArray messages = new JSONArray()
                .put(new JSONObject()
                        .put("role", "user")
                        .put("content", userInput));

        JSONObject requestBody = new JSONObject()
                .put("model", provider.model())
                .put("system", systemPrompt)
                .put("messages", messages);

        LlmProviderConfig.ProviderOptions options = provider.options();
        if (options != null) {
            requestBody.put("max_tokens", options.maxTokens() != null ? options.maxTokens() : DEFAULT_MAX_TOKENS);
            if (options.temperature() != null) {
                requestBody.put("temperature", options.temperature());
            }
            if (options.topP() != null) {
                requestBody.put("top_p", options.topP());
            }
        } else {
            requestBody.put("max_tokens", DEFAULT_MAX_TOKENS);
        }
        return requestBody;
    }

    private HttpHeaders buildHeaders(LlmProviderConfig provider) {
        HttpHeaders headers = new HttpHeaders();
        LlmProviderConfig.ProviderOptions options = provider.options();
        if (options != null && options.headers() != null) {
            options.headers().forEach(headers::set);
        }

        headers.set("x-api-key", provider.apiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        if (headers.getFirst("anthropic-version") == null) {
            headers.set("anthropic-version", DEFAULT_ANTHROPIC_VERSION);
        }
        return headers;
    }

    private String extractContent(String rawResponse) {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw new BusinessException("大模型返回空响应", "LLM_RESPONSE_EMPTY");
        }

        try {
            JSONObject fullResponse = new JSONObject(rawResponse);
            JSONArray contentBlocks = fullResponse.getJSONArray("content");
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < contentBlocks.length(); i++) {
                JSONObject block = contentBlocks.getJSONObject(i);
                if ("text".equals(block.optString("type"))) {
                    text.append(block.optString("text"));
                }
            }
            if (text.length() == 0) {
                throw new BusinessException("大模型返回内容格式无效", "LLM_RESPONSE_INVALID");
            }
            return text.toString();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            logger.error("提取Anthropic响应内容时发生错误: {}, 原始响应: {}", e.getMessage(), rawResponse, e);
            throw new BusinessException("大模型返回内容格式无效", "LLM_RESPONSE_INVALID");
        }
    }
}

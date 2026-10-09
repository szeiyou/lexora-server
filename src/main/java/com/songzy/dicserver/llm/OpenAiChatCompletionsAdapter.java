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
public class OpenAiChatCompletionsAdapter implements LlmProtocolAdapter {
    private static final Logger logger = LoggerFactory.getLogger(OpenAiChatCompletionsAdapter.class);

    private final RestTemplate restTemplate;

    public OpenAiChatCompletionsAdapter(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String protocol() {
        return "openai";
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
            logger.error("调用大模型接口失败: {}", e.getMessage(), e);
            throw new BusinessException("大模型接口调用失败", "LLM_API_ERROR");
        }
    }

    private JSONObject buildRequestBody(LlmProviderConfig provider, String userInput, String systemPrompt) {
        JSONArray messages = new JSONArray()
                .put(new JSONObject().put("role", "system").put("content", systemPrompt))
                .put(new JSONObject().put("role", "user").put("content", userInput));

        JSONObject requestBody = new JSONObject()
                .put("model", provider.model())
                .put("messages", messages);

        LlmProviderConfig.ProviderOptions options = provider.options();
        if (options != null) {
            if (options.temperature() != null) {
                requestBody.put("temperature", options.temperature());
            }
            if (options.topP() != null) {
                requestBody.put("top_p", options.topP());
            }
            if (options.maxTokens() != null) {
                requestBody.put("max_tokens", options.maxTokens());
            }
        }
        return requestBody;
    }

    private HttpHeaders buildHeaders(LlmProviderConfig provider) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(provider.apiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        LlmProviderConfig.ProviderOptions options = provider.options();
        if (options != null && options.headers() != null) {
            options.headers().forEach(headers::set);
        }
        return headers;
    }

    private String extractContent(String rawResponse) {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw new BusinessException("大模型返回空响应", "LLM_RESPONSE_EMPTY");
        }

        try {
            JSONObject fullResponse = new JSONObject(rawResponse);
            return fullResponse.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            logger.error("提取LLM响应内容时发生错误: {}, 原始响应: {}", e.getMessage(), rawResponse, e);
            throw new BusinessException("大模型返回内容格式无效", "LLM_RESPONSE_INVALID");
        }
    }
}

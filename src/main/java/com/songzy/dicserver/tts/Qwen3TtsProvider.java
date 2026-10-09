package com.songzy.dicserver.tts;

import com.songzy.dicserver.model.AudioType;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;

/**
 * Qwen3-TTS provider using HTTP API.
 */
@Component
public class Qwen3TtsProvider implements TtsProvider {
    private static final Logger logger = LoggerFactory.getLogger(Qwen3TtsProvider.class);

    private final RestTemplate restTemplate;

    @Value("${tts.qwen3.base-url:http://localhost:8000}")
    private String baseUrl;

    @Value("${tts.qwen3.api-key:}")
    private String apiKey;

    @Value("${tts.qwen3.speaker:vivian}")
    private String speaker;

    @Value("${tts.qwen3.generation.max-new-tokens:1024}")
    private int generationMaxNewTokens;

    @Value("${tts.qwen3.generation.temperature:0.1}")
    private double generationTemperature;

    @Value("${tts.qwen3.generation.top-k:20}")
    private int generationTopK;

    @Value("${tts.qwen3.generation.top-p:0.9}")
    private double generationTopP;

    @Value("${tts.qwen3.generation.repetition-penalty:1.05}")
    private double generationRepetitionPenalty;

    public Qwen3TtsProvider(@Qualifier("qwen3TtsRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String getName() {
        return "qwen3";
    }

    @Override
    public byte[] synthesize(String text, AudioType audioType) {
        logger.debug("调用Qwen3-TTS接口，文本长度: {} 字符，音色: {}", text.length(), speaker);
        try {
            String url = baseUrl + "/v1/tts/custom";

            JSONObject requestBody = new JSONObject();
            requestBody.put("text", text);
            requestBody.put("speaker", speaker);
            requestBody.put("format", "mp3");
            requestBody.put("generation_params", buildGenerationParams());
            applyAudioTypeDirectives(requestBody, audioType);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<String> entity = new HttpEntity<>(requestBody.toString(), headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JSONObject result = new JSONObject(response.getBody());
                String audioBase64 = result.getString("audio_base64");
                double duration = result.optDouble("duration_seconds", 0);
                double genTime = result.optDouble("generation_time_seconds", 0);
                logger.info("Qwen3 TTS合成成功，时长: {}s，生成耗时: {}s", duration, genTime);
                return Base64.getDecoder().decode(audioBase64);
            } else {
                String message = "Qwen3 TTS接口返回异常状态: " + response.getStatusCode();
                logger.error(message);
                throw new TtsSynthesisException(message);
            }
        } catch (TtsSynthesisException ex) {
            throw ex;
        } catch (Exception e) {
            String message = "Qwen3 TTS接口调用失败: " + e.getMessage();
            logger.error(message, e);
            throw new TtsSynthesisException(message, e);
        }
    }

    private void applyAudioTypeDirectives(JSONObject requestBody, AudioType audioType) {
        if (audioType == AudioType.HEADWORD) {
            requestBody.put("language", "English");
            requestBody.put("instruction", "标准美式英语朗读");
            return;
        }
        if (audioType == AudioType.FULL_READING) {
            requestBody.put("language", "Auto");
            requestBody.put("instruction", "请使用稳定一致的英语老师语气朗读，保持音色统一，语速自然");
            return;
        }
        requestBody.put("language", "Auto");
    }

    private JSONObject buildGenerationParams() {
        JSONObject generationParams = new JSONObject();
        generationParams.put("max_new_tokens", generationMaxNewTokens);
        generationParams.put("temperature", generationTemperature);
        generationParams.put("top_k", generationTopK);
        generationParams.put("top_p", generationTopP);
        generationParams.put("repetition_penalty", generationRepetitionPenalty);
        return generationParams;
    }

    @Override
    public boolean loadModels() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/admin/models/load", HttpMethod.POST, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                logger.warn("Qwen3 admin endpoint /admin/models/load returned {}", response.getStatusCode());
                return false;
            }
            if (response.getBody() == null || response.getBody().isBlank()) {
                return isProviderHealthy();
            }

            String status = new JSONObject(response.getBody()).optString("status");
            if ("already_loaded".equals(status) || "loaded".equals(status)) {
                return true;
            }
            if ("loading".equals(status)) {
                boolean healthy = isProviderHealthy();
                if (!healthy) {
                    logger.info("Qwen3 admin endpoint /admin/models/load reported loading; provider is not ready yet");
                }
                return healthy;
            }

            logger.warn("Qwen3 admin endpoint /admin/models/load returned unexpected status {}", status);
            return false;
        } catch (Exception e) {
            logger.warn("Qwen3 admin endpoint /admin/models/load failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean unloadModels() {
        return invokeAdminEndpoint("/admin/models/unload", "unloaded", "not_loaded");
    }

    private boolean invokeAdminEndpoint(String path, String... acceptedStatuses) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + path, HttpMethod.POST, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                logger.warn("Qwen3 admin endpoint {} returned {}", path, response.getStatusCode());
                return false;
            }
            if (response.getBody() == null || response.getBody().isBlank()) {
                return true;
            }

            String status = new JSONObject(response.getBody()).optString("status");
            for (String acceptedStatus : acceptedStatuses) {
                if (acceptedStatus.equals(status)) {
                    return true;
                }
            }
            logger.warn("Qwen3 admin endpoint {} returned unexpected status {}", path, status);
            return false;
        } catch (Exception e) {
            logger.warn("Qwen3 admin endpoint {} failed: {}", path, e.getMessage());
            return false;
        }
    }

    private boolean isProviderHealthy() {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/health", HttpMethod.GET, HttpEntity.EMPTY, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                logger.warn("Qwen3 health endpoint returned {}", response.getStatusCode());
                return false;
            }
            if (response.getBody() == null || response.getBody().isBlank()) {
                logger.warn("Qwen3 health endpoint returned an empty body");
                return false;
            }

            String status = new JSONObject(response.getBody()).optString("status");
            if ("ok".equals(status)) {
                return true;
            }
            logger.info("Qwen3 health endpoint reports status {}", status);
            return false;
        } catch (Exception e) {
            logger.warn("Qwen3 health endpoint check failed: {}", e.getMessage());
            return false;
        }
    }
}

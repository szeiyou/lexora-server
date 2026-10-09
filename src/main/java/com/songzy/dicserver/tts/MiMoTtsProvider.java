package com.songzy.dicserver.tts;

import com.songzy.dicserver.model.AudioType;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;

/**
 * Xiaomi MiMo TTS provider using the OpenAI-compatible Chat Completions API.
 *
 * <p>Unlike the self-hosted Qwen3 backend, MiMo expects the text to synthesize in an
 * {@code assistant} message and the style directive in a {@code user} message, with the
 * voice selected via the {@code audio} field. It is a managed cloud service, so model
 * load/unload are no-ops.
 *
 * <p>Only active when {@code tts.mimo.api-key} is configured.
 */
@Component
@ConditionalOnProperty(prefix = "tts.mimo", name = "api-key")
public class MiMoTtsProvider implements TtsProvider {
    private static final Logger logger = LoggerFactory.getLogger(MiMoTtsProvider.class);

    private final RestTemplate restTemplate;

    @Value("${tts.mimo.base-url:https://api.xiaomimimo.com/v1}")
    private String baseUrl;

    @Value("${tts.mimo.api-key:}")
    private String apiKey;

    @Value("${tts.mimo.model:mimo-v2.5-tts}")
    private String model;

    @Value("${tts.mimo.voice:Mia}")
    private String voice;

    @Value("${tts.mimo.format:mp3}")
    private String format;

    public MiMoTtsProvider(@Qualifier("mimoTtsRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String getName() {
        return "mimo";
    }

    @Override
    public byte[] synthesize(String text, AudioType audioType) {
        logger.debug("调用MiMo TTS接口，文本长度: {} 字符，音色: {}", text.length(), voice);
        try {
            String url = baseUrl + "/chat/completions";

            JSONObject requestBody = new JSONObject();
            requestBody.put("model", model);
            requestBody.put("messages", buildMessages(text, audioType));
            requestBody.put("audio", new JSONObject()
                    .put("voice", voice)
                    .put("format", format));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<String> entity = new HttpEntity<>(requestBody.toString(), headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return extractAudio(response.getBody());
            }
            String message = "MiMo TTS接口返回异常状态: " + response.getStatusCode();
            logger.error(message);
            throw new TtsSynthesisException(message);
        } catch (TtsSynthesisException ex) {
            throw ex;
        } catch (Exception e) {
            String message = "MiMo TTS接口调用失败: " + e.getMessage();
            logger.error(message, e);
            throw new TtsSynthesisException(message, e);
        }
    }

    private JSONArray buildMessages(String text, AudioType audioType) {
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject()
                .put("role", "user")
                .put("content", styleDirective(audioType)));
        messages.put(new JSONObject()
                .put("role", "assistant")
                .put("content", text));
        return messages;
    }

    private String styleDirective(AudioType audioType) {
        if (audioType == AudioType.FULL_READING) {
            return "Read aloud as a calm, consistent English teacher with a steady, natural pace and a uniform voice.";
        }
        return "Read clearly and slowly in standard American English, like a dictionary pronunciation.";
    }

    private byte[] extractAudio(String responseBody) {
        try {
            JSONObject result = new JSONObject(responseBody);
            JSONObject message = result.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message");
            JSONObject audio = message.getJSONObject("audio");
            String audioBase64 = audio.getString("data");
            byte[] decoded = Base64.getDecoder().decode(audioBase64);
            logger.info("MiMo TTS合成成功，音频长度: {} 字节", decoded.length);
            return decoded;
        } catch (JSONException e) {
            throw new TtsSynthesisException("MiMo TTS响应缺少音频数据: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean loadModels() {
        return true;
    }

    @Override
    public boolean unloadModels() {
        return true;
    }
}

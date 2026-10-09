package com.songzy.dicserver.tts;

import com.songzy.dicserver.model.AudioType;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

/**
 * Tencent Cloud TTS provider using WebSocket streaming API.
 * Only active when tts.tencent.secret-id is configured.
 */
@Component
@ConditionalOnProperty(prefix = "tts.tencent", name = {"secret-id", "secret-key"})
public class TencentTtsProvider implements TtsProvider {
    private static final Logger logger = LoggerFactory.getLogger(TencentTtsProvider.class);

    @Value("${tts.tencent.secret-id:}")
    private String secretId;

    @Value("${tts.tencent.secret-key:}")
    private String secretKey;

    @Value("${tts.tencent.app-id:}")
    private String appId;

    @Value("${tts.tencent.voice-type:502001}")
    private String voiceType;

    @Value("${tts.tencent.emotion-category:story}")
    private String emotionCategory;

    @Override
    public String getName() {
        return "tencent";
    }

    @Override
    public byte[] synthesize(String text, AudioType audioType) {
        logger.debug("调用腾讯TTS接口，文本长度: {} 字符", text.length());
        try {
            // 生成会话ID
            String sessionId = UUID.randomUUID().toString();

            // 生成时间戳和过期时间
            long timestamp = System.currentTimeMillis() / 1000;
            long expired = timestamp + 86400; // 24小时后过期

            // 构建参数Map，按字典序排序
            TreeMap<String, String> params = new TreeMap<>();
            params.put("Action", "TextToStreamAudioWS");
            params.put("AppId", appId);
            params.put("Codec", "mp3");
            params.put("EnableSubtitle", "false");
            params.put("Expired", String.valueOf(expired));
            params.put("SampleRate", "16000");
            params.put("SecretId", secretId);
            params.put("SessionId", sessionId);
            params.put("Speed", "0");
            params.put("Text", text);
            params.put("Timestamp", String.valueOf(timestamp));
            params.put("VoiceType", voiceType);
            params.put("EmotionCategory", emotionCategory);
            params.put("Volume", "5");

            // 生成签名原文
            StringBuilder signBuilder = new StringBuilder("GETtts.cloud.tencent.com/stream_ws?");
            for (Map.Entry<String, String> entry : params.entrySet()) {
                signBuilder.append(entry.getKey())
                          .append("=")
                          .append(entry.getValue())
                          .append("&");
            }
            String signStr = signBuilder.substring(0, signBuilder.length() - 1);

            // 生成签名
            Mac mac = Mac.getInstance("HmacSHA1");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), mac.getAlgorithm());
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(signStr.getBytes(StandardCharsets.UTF_8));
            String signature = Base64.getEncoder().encodeToString(hash);

            // 构建WebSocket URL
            StringBuilder urlBuilder = new StringBuilder("wss://tts.cloud.tencent.com/stream_ws?");
            for (Map.Entry<String, String> entry : params.entrySet()) {
                urlBuilder.append(entry.getKey())
                         .append("=")
                         .append(URLEncoder.encode(entry.getValue(), "UTF-8"))
                         .append("&");
            }
            urlBuilder.append("Signature=")
                     .append(URLEncoder.encode(signature, "UTF-8"));

            // 创建WebSocket客户端并连接
            ByteArrayOutputStream audioStream = new ByteArrayOutputStream();
            CountDownLatch completionLatch = new CountDownLatch(1);

            WebSocketClient client = new WebSocketClient(new URI(urlBuilder.toString())) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    logger.debug("WebSocket连接已建立");
                }

                @Override
                public void onMessage(String message) {
                    // 处理文本消息（状态和时间戳信息）
                    try {
                        JSONObject response = new JSONObject(message);
                        if (response.getInt("final") == 1) {
                            completionLatch.countDown();
                        }
                    } catch (Exception e) {
                        logger.error("处理文本消息时出错", e);
                    }
                }

                @Override
                public void onMessage(ByteBuffer bytes) {
                    // 处理二进制消息（音频数据）
                    try {
                        byte[] audio = new byte[bytes.remaining()];
                        bytes.get(audio);
                        audioStream.write(audio);
                    } catch (IOException e) {
                        logger.error("处理音频数据时出错", e);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    logger.debug("WebSocket连接已关闭: code={}, reason={}", code, reason);
                    completionLatch.countDown();
                }

                @Override
                public void onError(Exception ex) {
                    logger.error("WebSocket发生错误", ex);
                    completionLatch.countDown();
                }
            };

            // 连接并等待完成
            client.connect();
            completionLatch.await(30, TimeUnit.SECONDS); // 设置30秒超时
            client.close();

            // 返回合成的音频数据
            byte[] result = audioStream.toByteArray();
            if (result.length > 0) {
                logger.info("腾讯TTS合成成功，音频长度: {} 字节", result.length);
            } else {
                logger.warn("腾讯TTS合成返回空音频");
            }
            return result;

        } catch (Exception e) {
            logger.error("腾讯TTS接口调用失败: {}", e.getMessage(), e);
            return null;
        }
    }
}

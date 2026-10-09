package com.songzy.dicserver.tts;

import com.songzy.dicserver.model.AudioType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MiMoTtsProviderTest {

    @Mock
    private RestTemplate restTemplate;

    private MiMoTtsProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        provider = new MiMoTtsProvider(restTemplate);
        ReflectionTestUtils.setField(provider, "baseUrl", "https://api.xiaomimimo.com/v1");
        ReflectionTestUtils.setField(provider, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(provider, "model", "mimo-v2.5-tts");
        ReflectionTestUtils.setField(provider, "voice", "Mia");
        ReflectionTestUtils.setField(provider, "format", "mp3");
    }

    private static String chatResponseWithAudio(byte[] audioBytes) throws Exception {
        String audioBase64 = Base64.getEncoder().encodeToString(audioBytes);
        return new JSONObject()
                .put("choices", new JSONArray()
                        .put(new JSONObject()
                                .put("message", new JSONObject()
                                        .put("role", "assistant")
                                        .put("content", "")
                                        .put("audio", new JSONObject()
                                                .put("data", audioBase64)))))
                .toString();
    }

    @Test
    void shouldReturnProviderName() throws Exception {
        assertThat(provider.getName()).isEqualTo("mimo");
    }

    @Test
    void shouldSynthesizeTextSuccessfully() throws Exception {
        ResponseEntity<String> mockResponse =
                new ResponseEntity<>(chatResponseWithAudio(new byte[]{1, 2, 3}), HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockResponse);

        byte[] result = provider.synthesize("serendipity", AudioType.HEADWORD);

        assertThat(result).isEqualTo(new byte[]{1, 2, 3});
        verify(restTemplate).exchange(
                eq("https://api.xiaomimimo.com/v1/chat/completions"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class));
    }

    @Test
    void shouldPutSynthesisTextInAssistantMessageAndStyleInUserMessage() throws Exception {
        ResponseEntity<String> mockResponse =
                new ResponseEntity<>(chatResponseWithAudio(new byte[]{9}), HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockResponse);

        provider.synthesize("hello world", AudioType.HEADWORD);

        JSONObject requestBody = captureRequestBody();
        assertThat(requestBody.getString("model")).isEqualTo("mimo-v2.5-tts");
        JSONObject audio = requestBody.getJSONObject("audio");
        assertThat(audio.getString("voice")).isEqualTo("Mia");
        assertThat(audio.getString("format")).isEqualTo("mp3");

        JSONArray messages = requestBody.getJSONArray("messages");
        JSONObject userMessage = findMessage(messages, "user");
        JSONObject assistantMessage = findMessage(messages, "assistant");
        assertThat(assistantMessage.getString("content")).isEqualTo("hello world");
        assertThat(userMessage.getString("content")).isNotBlank();
    }

    @Test
    void shouldUseDictionaryStyleDirectiveForHeadwordAudio() throws Exception {
        ResponseEntity<String> mockResponse =
                new ResponseEntity<>(chatResponseWithAudio(new byte[]{9}), HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockResponse);

        provider.synthesize("hello", AudioType.HEADWORD);

        JSONObject requestBody = captureRequestBody();
        JSONObject userMessage = findMessage(requestBody.getJSONArray("messages"), "user");
        assertThat(userMessage.getString("content")).contains("American English");
    }

    @Test
    void shouldUseTeacherToneDirectiveForFullReadingAudio() throws Exception {
        ResponseEntity<String> mockResponse =
                new ResponseEntity<>(chatResponseWithAudio(new byte[]{8}), HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockResponse);

        provider.synthesize("a full reading passage", AudioType.FULL_READING);

        JSONObject requestBody = captureRequestBody();
        JSONObject userMessage = findMessage(requestBody.getJSONArray("messages"), "user");
        assertThat(userMessage.getString("content")).isNotBlank();
        JSONObject assistantMessage = findMessage(requestBody.getJSONArray("messages"), "assistant");
        assertThat(assistantMessage.getString("content")).isEqualTo("a full reading passage");
    }

    @Test
    void shouldSendBearerAuthorizationHeader() throws Exception {
        ResponseEntity<String> mockResponse =
                new ResponseEntity<>(chatResponseWithAudio(new byte[]{1}), HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockResponse);

        provider.synthesize("hello", AudioType.HEADWORD);

        ArgumentCaptor<HttpEntity<String>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));
        HttpHeaders headers = entityCaptor.getValue().getHeaders();
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer test-api-key");
        assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void shouldThrowOnHttpError() throws Exception {
        ResponseEntity<String> mockResponse = new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockResponse);

        assertThatThrownBy(() -> provider.synthesize("hello", AudioType.HEADWORD))
                .isInstanceOf(TtsSynthesisException.class)
                .hasMessageContaining("500 INTERNAL_SERVER_ERROR");
    }

    @Test
    void shouldThrowWhenAudioDataMissing() throws Exception {
        String body = new JSONObject()
                .put("choices", new JSONArray()
                        .put(new JSONObject().put("message", new JSONObject().put("content", ""))))
                .toString();
        ResponseEntity<String> mockResponse = new ResponseEntity<>(body, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockResponse);

        assertThatThrownBy(() -> provider.synthesize("hello", AudioType.HEADWORD))
                .isInstanceOf(TtsSynthesisException.class);
    }

    @Test
    void shouldThrowOnException() throws Exception {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> provider.synthesize("hello", AudioType.HEADWORD))
                .isInstanceOf(TtsSynthesisException.class)
                .hasMessageContaining("Connection refused");
    }

    @Test
    void loadModelsShouldBeNoOpReturningTrue() throws Exception {
        assertThat(provider.loadModels()).isTrue();
        verifyNoInteractions(restTemplate);
    }

    @Test
    void unloadModelsShouldBeNoOpReturningTrue() throws Exception {
        assertThat(provider.unloadModels()).isTrue();
        verifyNoInteractions(restTemplate);
    }

    private JSONObject captureRequestBody() throws Exception {
        ArgumentCaptor<HttpEntity<String>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));
        return new JSONObject(entityCaptor.getValue().getBody());
    }

    private static JSONObject findMessage(JSONArray messages, String role) throws Exception {
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.getJSONObject(i);
            if (role.equals(message.optString("role"))) {
                return message;
            }
        }
        throw new AssertionError("No message with role: " + role);
    }
}

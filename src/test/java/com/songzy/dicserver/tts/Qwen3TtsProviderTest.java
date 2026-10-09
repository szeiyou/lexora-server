package com.songzy.dicserver.tts;

import com.songzy.dicserver.model.AudioType;
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
class Qwen3TtsProviderTest {

    @Mock
    private RestTemplate restTemplate;

    private Qwen3TtsProvider provider;

    @BeforeEach
    void setUp() {
        provider = new Qwen3TtsProvider(restTemplate);
        ReflectionTestUtils.setField(provider, "baseUrl", "http://localhost:8000");
        ReflectionTestUtils.setField(provider, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(provider, "speaker", "vivian");
        ReflectionTestUtils.setField(provider, "generationMaxNewTokens", 1024);
        ReflectionTestUtils.setField(provider, "generationTemperature", 0.1d);
        ReflectionTestUtils.setField(provider, "generationTopK", 20);
        ReflectionTestUtils.setField(provider, "generationTopP", 0.9d);
        ReflectionTestUtils.setField(provider, "generationRepetitionPenalty", 1.05d);
    }

    @Test
    void shouldReturnProviderName() {
        assertThat(provider.getName()).isEqualTo("qwen3");
    }

    @Test
    void shouldSynthesizeTextSuccessfully() {
        String audioBase64 = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        String responseBody = String.format(
                "{\"audio_base64\": \"%s\", \"duration_seconds\": 1.5, \"generation_time_seconds\": 0.3}",
                audioBase64);

        ResponseEntity<String> mockResponse = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockResponse);

        byte[] result = provider.synthesize("hello world", AudioType.HEADWORD);

        assertThat(result).isEqualTo(new byte[]{1, 2, 3});
        verify(restTemplate).exchange(
                eq("http://localhost:8000/v1/tts/custom"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class));
    }

    @Test
    void shouldUseConfiguredBaseUrlWithoutForcingContainerLocalhost() {
        ReflectionTestUtils.setField(provider, "baseUrl", "http://host.docker.internal:8000");
        String audioBase64 = Base64.getEncoder().encodeToString(new byte[]{1});
        ResponseEntity<String> mockResponse = new ResponseEntity<>(
                String.format("{\"audio_base64\": \"%s\"}", audioBase64), HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockResponse);

        provider.synthesize("hello", AudioType.HEADWORD);

        verify(restTemplate).exchange(
                eq("http://host.docker.internal:8000/v1/tts/custom"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class));
    }

    @Test
    void shouldUseEnglishHeadwordInstructionForHeadwordAudio() throws Exception {
        String audioBase64 = Base64.getEncoder().encodeToString(new byte[]{9});
        ResponseEntity<String> mockResponse = new ResponseEntity<>(
                String.format("{\"audio_base64\": \"%s\"}", audioBase64), HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockResponse);

        provider.synthesize("hello", AudioType.HEADWORD);

        ArgumentCaptor<HttpEntity<String>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("http://localhost:8000/v1/tts/custom"),
                eq(HttpMethod.POST),
                entityCaptor.capture(),
                eq(String.class));

        JSONObject requestBody = new JSONObject(entityCaptor.getValue().getBody());
        assertThat(requestBody.getString("language")).isEqualTo("English");
        assertThat(requestBody.getString("instruction")).isEqualTo("标准美式英语朗读");
        JSONObject generationParams = requestBody.getJSONObject("generation_params");
        assertThat(generationParams.getInt("max_new_tokens")).isEqualTo(1024);
        assertThat(generationParams.getDouble("temperature")).isEqualTo(0.1d);
        assertThat(generationParams.getInt("top_k")).isEqualTo(20);
        assertThat(generationParams.getDouble("top_p")).isEqualTo(0.9d);
        assertThat(generationParams.getDouble("repetition_penalty")).isEqualTo(1.05d);
    }

    @Test
    void shouldUseStableTeacherToneInstructionForFullReadingAudio() throws Exception {
        String audioBase64 = Base64.getEncoder().encodeToString(new byte[]{8});
        ResponseEntity<String> mockResponse = new ResponseEntity<>(
                String.format("{\"audio_base64\": \"%s\"}", audioBase64), HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockResponse);

        provider.synthesize("hello reading", AudioType.FULL_READING);

        ArgumentCaptor<HttpEntity<String>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("http://localhost:8000/v1/tts/custom"),
                eq(HttpMethod.POST),
                entityCaptor.capture(),
                eq(String.class));

        JSONObject requestBody = new JSONObject(entityCaptor.getValue().getBody());
        assertThat(requestBody.getString("instruction")).isEqualTo("请使用稳定一致的英语老师语气朗读，保持音色统一，语速自然");
        assertThat(requestBody.getString("language")).isEqualTo("Auto");
        JSONObject generationParams = requestBody.getJSONObject("generation_params");
        assertThat(generationParams.getDouble("temperature")).isEqualTo(0.1d);
    }

    @Test
    void shouldThrowOnHttpError() {
        ResponseEntity<String> mockResponse = new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockResponse);

        assertThatThrownBy(() -> provider.synthesize("hello", AudioType.HEADWORD))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("500 INTERNAL_SERVER_ERROR");
    }

    @Test
    void shouldThrowOnException() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> provider.synthesize("hello", AudioType.HEADWORD))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Connection refused");
    }

    @Test
    void loadModelsShouldReturnTrueWhenProviderBecomesHealthyAfterLoadingStarts() {
        when(restTemplate.exchange(eq("http://localhost:8000/admin/models/load"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"status\":\"loading\"}", HttpStatus.OK));
        when(restTemplate.exchange(eq("http://localhost:8000/health"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"status\":\"ok\"}", HttpStatus.OK));

        boolean result = provider.loadModels();

        assertThat(result).isTrue();
        verify(restTemplate).exchange(eq("http://localhost:8000/health"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void loadModelsShouldReturnFalseWhenProviderIsStillLoadingAccordingToHealthEndpoint() {
        when(restTemplate.exchange(eq("http://localhost:8000/admin/models/load"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"status\":\"loading\"}", HttpStatus.OK));
        when(restTemplate.exchange(eq("http://localhost:8000/health"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"status\":\"loading\"}", HttpStatus.OK));

        boolean result = provider.loadModels();

        assertThat(result).isFalse();
    }

    @Test
    void loadModelsShouldReturnTrueWhenAlreadyLoaded() {
        when(restTemplate.exchange(eq("http://localhost:8000/admin/models/load"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"status\":\"already_loaded\"}", HttpStatus.OK));

        boolean result = provider.loadModels();

        assertThat(result).isTrue();
    }

    @Test
    void unloadModelsShouldReturnTrueWhenProviderIsUnloaded() {
        when(restTemplate.exchange(eq("http://localhost:8000/admin/models/unload"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"status\":\"unloaded\"}", HttpStatus.OK));

        boolean result = provider.unloadModels();

        assertThat(result).isTrue();
    }

    @Test
    void unloadModelsShouldReturnFalseWhenProviderReturnsUnexpectedStatus() {
        when(restTemplate.exchange(eq("http://localhost:8000/admin/models/unload"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"status\":\"still_loaded\"}", HttpStatus.OK));

        boolean result = provider.unloadModels();

        assertThat(result).isFalse();
    }
}

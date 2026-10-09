package com.songzy.dicserver.service;

import com.songzy.dicserver.model.AudioType;
import com.songzy.dicserver.tts.TtsProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TtsServiceTest {

    @Mock
    private TtsProvider qwen3Provider;

    @Mock
    private TtsProvider tencentProvider;

    private TtsService ttsService;

    @BeforeEach
    void setUp() {
        when(qwen3Provider.getName()).thenReturn("qwen3");
        when(tencentProvider.getName()).thenReturn("tencent");
        ttsService = new TtsService(List.of(qwen3Provider, tencentProvider), "qwen3");
    }

    @Test
    void shouldReturnDefaultProviderAsActive() {
        assertThat(ttsService.getActiveProvider()).isEqualTo("qwen3");
    }

    @Test
    void shouldReturnAllAvailableProviders() {
        List<String> providers = ttsService.getAvailableProviders();
        assertThat(providers).containsExactlyInAnyOrder("qwen3", "tencent");
    }

    @Test
    void shouldDelegateSynthesizeToActiveProvider() {
        byte[] expectedAudio = new byte[]{1, 2, 3};
        when(qwen3Provider.synthesize("test text", AudioType.HEADWORD)).thenReturn(expectedAudio);

        byte[] result = ttsService.synthesize("test text", AudioType.HEADWORD);

        assertThat(result).isEqualTo(expectedAudio);
        verify(qwen3Provider).synthesize("test text", AudioType.HEADWORD);
        verifyNoMoreInteractions(qwen3Provider);
        // tencentProvider.getName() was called during construction, so verifyNoMoreInteractions instead
        verify(tencentProvider, never()).synthesize(any(), any());
    }

    @Test
    void shouldSwitchProviderSuccessfully() {
        boolean result = ttsService.switchProvider("tencent");

        assertThat(result).isTrue();
        assertThat(ttsService.getActiveProvider()).isEqualTo("tencent");
    }

    @Test
    void shouldDelegateToNewProviderAfterSwitch() {
        byte[] expectedAudio = new byte[]{4, 5, 6};
        when(tencentProvider.synthesize("hello", AudioType.FULL_READING)).thenReturn(expectedAudio);

        ttsService.switchProvider("tencent");
        byte[] result = ttsService.synthesize("hello", AudioType.FULL_READING);

        assertThat(result).isEqualTo(expectedAudio);
        verify(tencentProvider).synthesize("hello", AudioType.FULL_READING);
        verify(qwen3Provider, never()).synthesize(any(), any());
    }

    @Test
    void shouldReturnFalseForUnknownProvider() {
        boolean result = ttsService.switchProvider("unknown");

        assertThat(result).isFalse();
        assertThat(ttsService.getActiveProvider()).isEqualTo("qwen3");
    }

    @Test
    void shouldReturnNullWhenProviderNotFound() {
        TtsService singleProviderService = new TtsService(List.of(qwen3Provider), "nonexistent");

        byte[] result = singleProviderService.synthesize("test", AudioType.HEADWORD);

        assertThat(result).isNull();
    }
}

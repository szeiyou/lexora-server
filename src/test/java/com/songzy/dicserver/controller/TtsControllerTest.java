package com.songzy.dicserver.controller;

import com.songzy.dicserver.service.TtsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TtsControllerTest {

    @Mock
    private TtsService ttsService;

    @InjectMocks
    private TtsController ttsController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(ttsController).build();
    }

    @Test
    void getProvidersShouldReturnActiveAndAvailable() throws Exception {
        when(ttsService.getActiveProvider()).thenReturn("qwen3");
        when(ttsService.getAvailableProviders()).thenReturn(List.of("qwen3", "tencent"));

        mockMvc.perform(get("/api/v1/tts/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value("qwen3"))
                .andExpect(jsonPath("$.available").isArray())
                .andExpect(jsonPath("$.available[0]").value("qwen3"))
                .andExpect(jsonPath("$.available[1]").value("tencent"));
    }

    @Test
    void switchProviderShouldReturnNewActiveProvider() throws Exception {
        when(ttsService.switchProvider("tencent")).thenReturn(true);

        mockMvc.perform(post("/api/v1/tts/switch/tencent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value("tencent"));
    }

    @Test
    void switchProviderShouldReturnBadRequestForUnknownProvider() throws Exception {
        when(ttsService.switchProvider("unknown")).thenReturn(false);
        when(ttsService.getAvailableProviders()).thenReturn(List.of("qwen3"));

        mockMvc.perform(post("/api/v1/tts/switch/unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}

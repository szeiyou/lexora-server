package com.songzy.dicserver.controller;

import com.songzy.dicserver.service.TtsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/tts")
@Tag(name = "TTS管理", description = "TTS提供商查询和切换接口")
public class TtsController {

    private final TtsService ttsService;

    public TtsController(TtsService ttsService) {
        this.ttsService = ttsService;
    }

    @GetMapping("/providers")
    @Operation(summary = "获取TTS提供商列表", description = "返回当前活跃的TTS提供商和所有可用的提供商列表")
    @ApiResponse(responseCode = "200", description = "获取成功")
    public ResponseEntity<Map<String, Object>> getProviders() {
        return ResponseEntity.ok(Map.of(
                "active", ttsService.getActiveProvider(),
                "available", ttsService.getAvailableProviders()
        ));
    }

    @PostMapping("/switch/{name}")
    @Operation(summary = "切换TTS提供商", description = "切换当前活跃的TTS提供商")
    @ApiResponse(responseCode = "200", description = "切换成功")
    @ApiResponse(responseCode = "400", description = "提供商不存在")
    public ResponseEntity<Map<String, String>> switchProvider(@PathVariable String name) {
        boolean success = ttsService.switchProvider(name);
        if (success) {
            return ResponseEntity.ok(Map.of("active", name));
        }
        return ResponseEntity.badRequest()
                .body(Map.of("error", "TTS provider '" + name + "' not found",
                        "available", String.join(", ", ttsService.getAvailableProviders())));
    }
}

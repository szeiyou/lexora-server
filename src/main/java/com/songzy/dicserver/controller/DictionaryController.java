package com.songzy.dicserver.controller;

import com.songzy.dicserver.dto.QueryHistoryDetailDTO;
import com.songzy.dicserver.dto.QueryHistorySummaryDTO;
import com.songzy.dicserver.dto.TextTranslationRequest;
import com.songzy.dicserver.exception.BusinessException;
import com.songzy.dicserver.model.EntryQueryResponse;
import com.songzy.dicserver.model.EntryResultType;
import com.songzy.dicserver.model.TextTranslationResponse;
import com.songzy.dicserver.security.CurrentUserPrincipal;
import com.songzy.dicserver.service.AudioGenerationTaskService;
import com.songzy.dicserver.service.AudioKeyBuilder;
import com.songzy.dicserver.service.EntryAudioService;
import com.songzy.dicserver.service.EntryQueryService;
import com.songzy.dicserver.service.QueryHistoryService;
import com.songzy.dicserver.service.TextTranslationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
@RestController
@RequestMapping("/api/v1")
@Validated
@Tag(name = "词典", description = "单词查询、音频、搜索历史相关接口")
public class DictionaryController {

    @Autowired
    private EntryQueryService entryQueryService;

    @Autowired
    private EntryAudioService entryAudioService;

    @Autowired
    private AudioGenerationTaskService audioGenerationTaskService;

    @Autowired
    private QueryHistoryService queryHistoryService;

    @Autowired
    private TextTranslationService textTranslationService;

    @GetMapping("/entries")
    @Operation(summary = "通用词典查询", description = "支持英文单词、汉译英词语以及中英文句子互译")
    public EntryQueryResponse getEntry(
            @AuthenticationPrincipal CurrentUserPrincipal currentUser,
            @RequestParam("q") String query,
            @RequestParam(value = "type", required = false) EntryResultType type) {
        validateEntryQuery(query);
        validateEntryType(type);
        return entryQueryService.query(currentUser.userId(), query.trim(), type);
    }

    @PostMapping("/entries/refresh")
    @Operation(summary = "刷新通用词典查询", description = "强制绕过缓存刷新查询结果")
    public EntryQueryResponse refreshEntry(
            @AuthenticationPrincipal CurrentUserPrincipal currentUser,
            @RequestParam("q") String query,
            @RequestParam(value = "type", required = false) EntryResultType type) {
        validateEntryQuery(query);
        validateEntryType(type);
        return entryQueryService.refresh(currentUser.userId(), query.trim(), type);
    }

    @PostMapping("/text-translations")
    @Operation(summary = "短文翻译", description = "使用 JSON body 发起中英文短文翻译")
    public TextTranslationResponse translateText(@AuthenticationPrincipal CurrentUserPrincipal currentUser,
                                                 @Valid @RequestBody TextTranslationRequest request) {
        String trimmedText = validateAndTrimTranslationText(request);
        return textTranslationService.translate(
                currentUser.userId(),
                trimmedText,
                request.resolvedSourceLanguage(),
                request.getTargetLanguage()
        );
    }

    @GetMapping("/history")
    @Operation(summary = "搜索历史", description = "分页查询搜索历史记录")
    public Page<QueryHistorySummaryDTO> getSearchHistory(@AuthenticationPrincipal CurrentUserPrincipal currentUser,
                                                         Pageable pageable) {
        return queryHistoryService.getHistory(currentUser.userId(), pageable);
    }

    @GetMapping("/history/{historyKey}")
    @Operation(summary = "搜索历史详情", description = "按 historyKey 查询搜索历史详情")
    public ResponseEntity<QueryHistoryDetailDTO> getSearchHistoryDetail(
            @AuthenticationPrincipal CurrentUserPrincipal currentUser,
            @PathVariable String historyKey
    ) {
        return ResponseEntity.of(queryHistoryService.getHistoryDetail(currentUser.userId(), historyKey));
    }

    @GetMapping("/audio/by-key/{audioKey}")
    @Operation(summary = "按音频键获取发音", description = "获取通用词典结果中的词头、短语或整条朗读音频")
    public ResponseEntity<byte[]> getAudioByKey(@PathVariable String audioKey) {
        String normalizedAudioKey = AudioKeyBuilder.normalizeAudioKey(audioKey);
        byte[] audioData = entryAudioService.getAudioByKey(normalizedAudioKey);
        if (audioData == null) {
            if (audioGenerationTaskService.isAwaitingAudio(normalizedAudioKey)) {
                return ResponseEntity.accepted().build();
            }
            return ResponseEntity.notFound().build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("audio/mpeg"));
        headers.setContentLength(audioData.length);

        return new ResponseEntity<>(audioData, headers, HttpStatus.OK);
    }
    private void validateEntryQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            throw new BusinessException("参数校验失败: q 不能为空", "VALIDATION_ERROR");
        }
        if (query.trim().length() > 300) {
            throw new BusinessException("参数校验失败: q 长度不能超过300", "VALIDATION_ERROR");
        }
    }

    private void validateEntryType(EntryResultType type) {
        if (type == EntryResultType.TEXT_TRANSLATION) {
            throw new BusinessException("参数校验失败: type 不支持 TEXT_TRANSLATION", "VALIDATION_ERROR");
        }
    }

    private String validateAndTrimTranslationText(TextTranslationRequest request) {
        String trimmedText = request.trimmedText();
        if (trimmedText == null || trimmedText.isEmpty()) {
            throw new BusinessException("参数校验失败: text 不能为空", "VALIDATION_ERROR");
        }
        if (trimmedText.length() > 3000) {
            throw new BusinessException("参数校验失败: text 长度不能超过3000", "VALIDATION_ERROR");
        }
        return trimmedText;
    }
}

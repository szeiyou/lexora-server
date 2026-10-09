package com.songzy.dicserver.controller;

import com.songzy.dicserver.model.Wordbook;
import com.songzy.dicserver.service.WordbookService;
import com.songzy.dicserver.dto.WordbookEntryDTO;
import com.songzy.dicserver.security.CurrentUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wordbooks")
@Tag(name = "单词本", description = "单词本管理相关接口")
public class WordbookController {

    @Autowired
    private WordbookService wordbookService;

    @GetMapping
    @Operation(summary = "获取所有单词本", description = "返回所有单词本列表")
    @ApiResponse(responseCode = "200", description = "查询成功")
    public List<Wordbook> getAllWordbooks(@AuthenticationPrincipal CurrentUserPrincipal currentUser) {
        return wordbookService.getAllWordbooks(currentUser.userId());
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取单词本详情", description = "根据ID获取单词本详情")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(responseCode = "404", description = "单词本不存在")
    public Wordbook getWordbook(
        @AuthenticationPrincipal CurrentUserPrincipal currentUser,
        @Parameter(description = "单词本ID") @PathVariable Long id) {
        return wordbookService.getWordbookById(currentUser.userId(), id);
    }

    @PostMapping
    @Operation(summary = "创建单词本", description = "创建一个新的单词本")
    @ApiResponse(responseCode = "200", description = "创建成功")
    public Wordbook createWordbook(
        @AuthenticationPrincipal CurrentUserPrincipal currentUser,
        @Parameter(description = "单词本名称", example = "我的生词本") @RequestParam @NotBlank @Size(max = 100) String name) {
        return wordbookService.createWordbook(currentUser.userId(), name.trim());
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新单词本名称", description = "根据ID更新单词本名称")
    @ApiResponse(responseCode = "200", description = "更新成功")
    @ApiResponse(responseCode = "404", description = "单词本不存在")
    public Wordbook updateWordbook(
        @AuthenticationPrincipal CurrentUserPrincipal currentUser,
        @Parameter(description = "单词本ID") @PathVariable Long id,
        @Parameter(description = "新的单词本名称") @RequestParam @NotBlank @Size(max = 100) String name) {
        return wordbookService.updateWordbook(currentUser.userId(), id, name.trim());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除单词本", description = "根据ID删除单词本及其所有单词")
    @ApiResponse(responseCode = "200", description = "删除成功")
    @ApiResponse(responseCode = "404", description = "单词本不存在")
    public ResponseEntity<Void> deleteWordbook(
        @AuthenticationPrincipal CurrentUserPrincipal currentUser,
        @Parameter(description = "单词本ID") @PathVariable Long id) {
        wordbookService.deleteWordbook(currentUser.userId(), id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/words")
    @Operation(summary = "获取单词本中的单词", description = "获取指定单词本中的所有单词及释义")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(responseCode = "404", description = "单词本不存在")
    public List<WordbookEntryDTO> getWordbookEntries(
        @AuthenticationPrincipal CurrentUserPrincipal currentUser,
        @Parameter(description = "单词本ID") @PathVariable Long id) {
        return wordbookService.getWordbookEntries(currentUser.userId(), id);
    }

    @PostMapping("/{id}/words")
    @Operation(summary = "添加单词到单词本", description = "向指定单词本批量添加单词")
    @ApiResponse(responseCode = "200", description = "添加成功")
    @ApiResponse(responseCode = "404", description = "单词本不存在")
    public ResponseEntity<Void> addWords(
            @AuthenticationPrincipal CurrentUserPrincipal currentUser,
            @Parameter(description = "单词本ID") @PathVariable Long id,
            @Valid @RequestBody @Size(max = 100) List<@NotBlank String> words) {
        wordbookService.addWordsToWordbook(currentUser.userId(), id, words.stream().map(String::trim).toList());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/words")
    @Operation(summary = "从单词本删除单词", description = "从指定单词本中批量删除单词")
    @ApiResponse(responseCode = "200", description = "删除成功")
    @ApiResponse(responseCode = "404", description = "单词本不存在")
    public ResponseEntity<Void> removeWords(
            @AuthenticationPrincipal CurrentUserPrincipal currentUser,
            @Parameter(description = "单词本ID") @PathVariable Long id,
            @RequestBody List<String> words) {
        wordbookService.removeWordsFromWordbook(currentUser.userId(), id, words);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/words/all")
    @Operation(summary = "清空单词本", description = "删除指定单词本中的所有单词")
    @ApiResponse(responseCode = "200", description = "清空成功")
    @ApiResponse(responseCode = "404", description = "单词本不存在")
    public ResponseEntity<Void> clearWordbook(
        @AuthenticationPrincipal CurrentUserPrincipal currentUser,
        @Parameter(description = "单词本ID") @PathVariable Long id) {
        wordbookService.clearWordbook(currentUser.userId(), id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/containing")
    @Operation(summary = "查找包含单词的单词本", description = "查找包含指定单词的所有单词本ID列表")
    @ApiResponse(responseCode = "200", description = "查询成功")
    public List<Long> findWordbooksContainingWord(
        @AuthenticationPrincipal CurrentUserPrincipal currentUser,
        @Parameter(description = "要查找的单词", example = "hello") @RequestParam @NotBlank @Size(max = 100) String word) {
        return wordbookService.findWordbooksContainingWord(currentUser.userId(), word.trim());
    }
}

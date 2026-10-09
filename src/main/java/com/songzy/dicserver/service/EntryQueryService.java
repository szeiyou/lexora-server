package com.songzy.dicserver.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songzy.dicserver.model.EntryQueryResponse;
import com.songzy.dicserver.model.EntryResultType;
import org.springframework.stereotype.Service;

@Service
public class EntryQueryService {

    private static final ObjectMapper CACHE_COPY_OBJECT_MAPPER = new ObjectMapper();

    private final EntryCacheService entryCacheService;
    private final EntryPromptBuilder entryPromptBuilder;
    private final LlmService llmService;
    private final EntryResponseParser entryResponseParser;
    private final EntryAudioService entryAudioService;
    private final QueryTextNormalizer queryTextNormalizer;
    private final QueryHistoryService queryHistoryService;
    private final QueryResultSnapshotService queryResultSnapshotService;

    public EntryQueryService(EntryCacheService entryCacheService,
                             EntryPromptBuilder entryPromptBuilder,
                             LlmService llmService,
                             EntryResponseParser entryResponseParser,
                             EntryAudioService entryAudioService,
                             QueryTextNormalizer queryTextNormalizer,
                             QueryHistoryService queryHistoryService,
                             QueryResultSnapshotService queryResultSnapshotService) {
        this.entryCacheService = entryCacheService;
        this.entryPromptBuilder = entryPromptBuilder;
        this.llmService = llmService;
        this.entryResponseParser = entryResponseParser;
        this.entryAudioService = entryAudioService;
        this.queryTextNormalizer = queryTextNormalizer;
        this.queryHistoryService = queryHistoryService;
        this.queryResultSnapshotService = queryResultSnapshotService;
    }

    public EntryQueryResponse query(Long userId, String rawQuery, EntryResultType requestedType) {
        return execute(userId, rawQuery, requestedType, false);
    }

    public EntryQueryResponse refresh(Long userId, String rawQuery, EntryResultType requestedType) {
        return execute(userId, rawQuery, requestedType, true);
    }

    private EntryQueryResponse execute(Long userId, String rawQuery, EntryResultType requestedType, boolean bypassCache) {
        String normalizedQuery = queryTextNormalizer.normalizeForStorage(rawQuery);

        if (requestedType == null) {
            if (!bypassCache) {
                EntryQueryResponse persistedResponse = queryResultSnapshotService.findEntrySnapshot(normalizedQuery, null)
                        .map(this::enrichAudioIfNeeded)
                        .orElse(null);
                if (persistedResponse != null) {
                    onSuccessfulResponse(userId, rawQuery, normalizedQuery, persistedResponse, false);
                    String cacheKey = entryCacheService.buildCacheKey(persistedResponse.getResultType(), normalizedQuery);
                    entryCacheService.put(cacheKey, persistedResponse);
                    return persistedResponse;
                }
            }

            EntryQueryResponse response = fetchLlmSelectedType(normalizedQuery);
            onSuccessfulResponse(userId, rawQuery, normalizedQuery, response, true);
            queryResultSnapshotService.upsertEntrySnapshot(normalizedQuery, response);
            String cacheKey = entryCacheService.buildCacheKey(response.getResultType(), normalizedQuery);
            entryCacheService.put(cacheKey, response);
            return response;
        }

        String requestedCacheKey = entryCacheService.buildCacheKey(requestedType, normalizedQuery);
        if (!bypassCache) {
            EntryQueryResponse cachedResponse = entryCacheService.get(requestedCacheKey)
                    .map(this::copyCachedResponse)
                    .map(this::enrichAudioIfNeeded)
                    .orElse(null);
            if (cachedResponse != null) {
                onSuccessfulResponse(userId, rawQuery, normalizedQuery, cachedResponse, false);
                entryCacheService.put(requestedCacheKey, cachedResponse);
                return cachedResponse;
            }

            EntryQueryResponse persistedResponse = queryResultSnapshotService.findEntrySnapshot(normalizedQuery, requestedType)
                    .map(this::enrichAudioIfNeeded)
                    .orElse(null);
            if (persistedResponse != null) {
                onSuccessfulResponse(userId, rawQuery, normalizedQuery, persistedResponse, false);
                entryCacheService.put(requestedCacheKey, persistedResponse);
                return persistedResponse;
            }
        }

        EntryQueryResponse freshResponse = fetch(normalizedQuery, requestedType);
        onSuccessfulResponse(userId, rawQuery, normalizedQuery, freshResponse, true);
        queryResultSnapshotService.upsertEntrySnapshot(normalizedQuery, freshResponse);
        entryCacheService.put(requestedCacheKey, freshResponse);
        return freshResponse;
    }

    private EntryQueryResponse fetchLlmSelectedType(String normalizedQuery) {
        String prompt = entryPromptBuilder.buildPrompt(null);
        String rawJson = llmService.queryRawJson(normalizedQuery, prompt);
        EntryQueryResponse response = entryResponseParser.parse(rawJson, null);
        return enrichAudioIfNeeded(response);
    }

    private EntryQueryResponse fetch(String normalizedQuery,
                                     EntryResultType requestedType) {
        String prompt = entryPromptBuilder.buildPrompt(requestedType);
        String rawJson = llmService.queryRawJson(normalizedQuery, prompt);
        EntryQueryResponse response = entryResponseParser.parse(rawJson, requestedType);
        return enrichAudioIfNeeded(response);
    }

    private EntryQueryResponse enrichAudioIfNeeded(EntryQueryResponse response) {
        if (response.getResultType() == EntryResultType.ENGLISH_WORD
                || response.getResultType() == EntryResultType.ZH_TO_EN_TERM) {
            return entryAudioService.enrichHeadwordAudio(response);
        }
        return response;
    }

    private EntryQueryResponse copyCachedResponse(EntryQueryResponse cachedResponse) {
        try {
            return CACHE_COPY_OBJECT_MAPPER.readValue(
                    CACHE_COPY_OBJECT_MAPPER.writeValueAsBytes(cachedResponse),
                    EntryQueryResponse.class
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deep-copy cached entry response", ex);
        }
    }

    private void onSuccessfulResponse(Long userId,
                                      String rawQuery,
                                      String normalizedQuery,
                                      EntryQueryResponse response,
                                      boolean freshResult) {
        queryHistoryService.recordSuccessWithSnapshot(
                userId,
                rawQuery,
                normalizedQuery,
                response,
                freshResult
        );
    }

}

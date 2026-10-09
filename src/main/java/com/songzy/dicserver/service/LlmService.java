package com.songzy.dicserver.service;

import com.songzy.dicserver.exception.BusinessException;
import com.songzy.dicserver.llm.LlmProtocolAdapter;
import com.songzy.dicserver.llm.LlmProviderConfig;
import com.songzy.dicserver.llm.LlmProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LlmService {
    private static final Logger logger = LoggerFactory.getLogger(LlmService.class);

    private final LlmProviderRegistry providerRegistry;
    private final Map<String, LlmProtocolAdapter> adapterByProtocol;

    public LlmService(LlmProviderRegistry providerRegistry, List<LlmProtocolAdapter> adapters) {
        this.providerRegistry = providerRegistry;
        this.adapterByProtocol = adapters.stream()
                .collect(Collectors.toUnmodifiableMap(LlmProtocolAdapter::protocol, Function.identity()));
    }

    public String queryRawJson(String userInput, String systemPrompt) {
        List<String> errors = new ArrayList<>();

        for (LlmProviderConfig provider : providerRegistry.getOrderedProviders()) {
            try {
                logger.info("尝试LLM提供者 [{}] 通过协议 [{}] 查询内容: {}", provider.name(), provider.protocol(), userInput);
                LlmProtocolAdapter adapter = resolveAdapter(provider.protocol());
                String rawContent = adapter.queryRawJson(provider, userInput, systemPrompt);
                return JsonContentSanitizer.sanitize(rawContent);
            } catch (BusinessException e) {
                logger.warn("LLM提供者 [{}] 调用失败: {} (code={}), 尝试下一个提供者",
                        provider.name(), e.getMessage(), e.getCode());
                errors.add(provider.name() + ": " + e.getMessage());
            } catch (Exception e) {
                logger.warn("LLM提供者 [{}] 发生未知错误: {}, 尝试下一个提供者",
                        provider.name(), e.getMessage(), e);
                errors.add(provider.name() + ": " + e.getMessage());
            }
        }

        String allErrors = String.join("; ", errors);
        logger.error("所有LLM提供者均调用失败: {}", allErrors);
        throw new BusinessException("所有LLM提供者均调用失败: " + allErrors, "ALL_LLM_PROVIDERS_FAILED");
    }

    private LlmProtocolAdapter resolveAdapter(String protocol) {
        LlmProtocolAdapter adapter = adapterByProtocol.get(protocol);
        if (adapter == null) {
            throw new BusinessException("未找到协议适配器: " + protocol, "LLM_PROTOCOL_NOT_SUPPORTED");
        }
        return adapter;
    }
}

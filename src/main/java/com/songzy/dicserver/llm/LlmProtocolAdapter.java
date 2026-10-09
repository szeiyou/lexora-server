package com.songzy.dicserver.llm;

public interface LlmProtocolAdapter {
    String protocol();

    String queryRawJson(LlmProviderConfig provider, String userInput, String systemPrompt);
}

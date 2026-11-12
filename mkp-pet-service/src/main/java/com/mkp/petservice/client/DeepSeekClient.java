package com.mkp.petservice.client;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

/**
 * DeepSeek 对话客户端（非流式），用于宠物养护建议。
 *
 * 已切换到 Spring AI ChatClient：模型参数（model / temperature / max-tokens）
 * 由 application.yaml 的 spring.ai.deepseek.chat.options 统一配置。
 */
@Service
public class DeepSeekClient {

    private final ChatClient chatClient;

    public DeepSeekClient(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    public String chat(String systemPrompt, String userPrompt) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
    }
}
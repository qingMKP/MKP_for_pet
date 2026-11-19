package com.mkp.chatbot.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 短期记忆：窗口 20 条消息（≈10 轮），超出后由 MessageWindowChatMemory 自动从头部裁剪。
 */
@Configuration
public class ChatMemoryConfig {

    private static final int MAX_MESSAGES = 20;

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(MAX_MESSAGES)
                .build();
    }
}

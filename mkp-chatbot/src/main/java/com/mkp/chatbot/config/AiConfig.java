package com.mkp.chatbot.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 配置。
 *
 * 对话与向量模型均由 Spring AI Alibaba 的 DashScope Starter 自动装配：
 * spring.ai.dashscope.* 管理 Qwen 对话与 text-embedding-v3 向量化，
 * spring.ai.model.chat 决定当前使用哪个对话模型（dashscope / deepseek）。
 */
@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}

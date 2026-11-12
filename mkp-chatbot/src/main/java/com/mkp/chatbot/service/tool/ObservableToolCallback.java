package com.mkp.chatbot.service.tool;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.function.BiConsumer;

/**
 * 工具回调装饰器。
 *
 * 在工具执行前后各触发一次回调，用于向 SSE 推送 action / observation，
 * 让前端能继续显示工具调用卡片与检索状态。
 */
public class ObservableToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final BiConsumer<String, String> listener;

    public ObservableToolCallback(ToolCallback delegate, BiConsumer<String, String> listener) {
        this.delegate = delegate;
        this.listener = listener;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        listener.accept("action", toolInput);
        String result = delegate.call(toolInput, toolContext);
        listener.accept("observation", result);
        return result;
    }
}
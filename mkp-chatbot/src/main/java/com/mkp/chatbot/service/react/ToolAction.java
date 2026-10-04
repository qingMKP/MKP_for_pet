package com.mkp.chatbot.service.react;

import lombok.Data;

import java.util.Map;

/**
 * 工具动作
 *
 * 表示 LLM 决定调用某个工具及其参数
 */
@Data
public class ToolAction {

    /**
     * 工具名称
     * 对应 ToolRegistry 中注册的工具
     */
    private String name;

    /**
     * 工具参数
     * 由 LLM 根据工具 schema 生成
     */
    private Map<String, Object> parameters;

    @Override
    public String toString() {
        return name + parameters;
    }
}

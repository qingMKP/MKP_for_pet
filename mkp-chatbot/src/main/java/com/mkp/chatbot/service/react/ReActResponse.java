package com.mkp.chatbot.service.react;

import lombok.Data;

/**
 * ReAct 响应数据结构
 *
 * 解析 LLM 的 ReAct 格式输出：
 * Thought: ...
 * Action: ...
 * Final Answer: ...
 */
@Data
public class ReActResponse {

    /**
     * 推理过程
     * LLM 对当前问题的分析和思考
     */
    private String thought;

    /**
     * 要执行的动作
     * null 或 name="finish" 表示不需要工具
     */
    private ToolAction action;

    /**
     * 最终答案
     * 当 action 为 finish 时，给用户的回答
     */
    private String finalAnswer;

    /**
     * 是否已完成（不需要继续循环）
     */
    public boolean isFinished() {
        return action == null || "finish".equalsIgnoreCase(action.getName());
    }
}

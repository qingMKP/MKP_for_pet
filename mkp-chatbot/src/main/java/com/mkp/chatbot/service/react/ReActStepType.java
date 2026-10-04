package com.mkp.chatbot.service.react;

/**
 * ReAct 步骤类型
 */
public enum ReActStepType {

    /**
     * 思考阶段
     */
    THOUGHT,

    /**
     * 行动阶段（调用工具）
     */
    ACTION,

    /**
     * 观察阶段（工具返回结果）
     */
    OBSERVATION,

    /**
     * 完成阶段（给出最终答案）
     */
    FINISH
}

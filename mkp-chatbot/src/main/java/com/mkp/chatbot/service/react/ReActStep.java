package com.mkp.chatbot.service.react;

import lombok.Data;

/**
 * ReAct 推理步骤记录
 *
 * 记录一轮完整的 Thought -> Action -> Observation
 */
@Data
public class ReActStep {

    /**
     * 轮次编号（从1开始）
     */
    private int iteration;

    /**
     * 步骤类型
     */
    private ReActStepType type;

    /**
     * 推理过程
     */
    private String thought;

    /**
     * 执行的动作（仅当 type = ACTION 时有值）
     */
    private ToolAction action;

    /**
     * 观察结果（仅当 type = ACTION 时有值）
     */
    private String observation;

    /**
     * 最终答案（仅当 type = FINISH 时有值）
     */
    private String finalAnswer;
}

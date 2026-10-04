package com.mkp.chatbot.service.react;

import com.mkp.chatbot.client.DeepSeekApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ReAct 推理引擎
 *
 * 职责：
 * 1. 管理 Thought -> Action -> Observation 的循环
 * 2. 控制最大迭代轮次
 * 3. 记录完整推理链
 * 4. 处理工具调用和结果观察
 * 5. 流式版本通过 SseEmitter 将 ReAct 全流程推送给前端
 */
@Component
@Slf4j
public class ReActEngine {

    @Autowired
    private DeepSeekApiClient deepSeekApiClient;

    @Autowired
    private ToolRegistry toolRegistry;

    private static final int MAX_ITERATIONS = 5;

    // ==================== 同步版本（保留作为降级兜底） ====================

    /**
     * 执行 ReAct 推理循环（同步版本）
     */
    public String execute(List<Message> messages, String userQuestion, ReActContext context) {
        List<ReActStep> reasoningChain = new ArrayList<>();
        String finalAnswer = null;

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            log.info("=== ReAct 第 {} 轮开始 | 用户问题: {} ===", i + 1, userQuestion);

            ReActResponse response;
            try {
                response = deepSeekApiClient.reactThink(messages, reasoningChain);
            } catch (Exception e) {
                log.error("LLM 调用失败: {}", e.getMessage());
                break;
            }

            if (response.getThought() == null) {
                log.error("LLM 未返回 Thought，终止循环");
                break;
            }

            log.info("Thought: {}", response.getThought());

            ReActStep step = new ReActStep();
            step.setIteration(i + 1);
            step.setThought(response.getThought());

            if (response.isFinished()) {
                finalAnswer = response.getFinalAnswer();
                step.setType(ReActStepType.FINISH);
                step.setFinalAnswer(finalAnswer);
                reasoningChain.add(step);
                log.info("Action: finish");
                log.info("最终答案: {}", finalAnswer);
                log.info("=== ReAct 循环完成，共 {} 轮 ===", i + 1);
                break;
            }

            ToolAction action = response.getAction();
            log.info("Action: {}({})", action.getName(), action.getParameters());

            long toolStartTime = System.currentTimeMillis();
            String observation;
            try {
                Tool tool = toolRegistry.getTool(action.getName());
                observation = tool.execute(action.getParameters(), context);
                log.info("工具执行耗时: {}ms", System.currentTimeMillis() - toolStartTime);
            } catch (ToolNotFoundException e) {
                log.error("工具未找到: {}", e.getMessage());
                observation = "工具执行失败: " + e.getMessage();
            } catch (Exception e) {
                log.error("工具执行失败: {}", e.getMessage());
                observation = "工具执行失败: " + e.getMessage();
            }

            log.info("Observation: {}", observation);

            step.setType(ReActStepType.ACTION);
            step.setAction(action);
            step.setObservation(observation);
            reasoningChain.add(step);

            messages.add(new Message("assistant", formatReActContext(
                response.getThought(), action, observation
            )));

            log.info("=== ReAct 第 {} 轮结束 ===", i + 1);
        }

        saveReasoningChain(userQuestion, reasoningChain);

        if (finalAnswer == null) {
            finalAnswer = generateFallbackAnswer(reasoningChain);
            log.warn("达到最大轮次 {}，给出兜底回答", MAX_ITERATIONS);
        }

        return finalAnswer;
    }

    // ==================== 流式版本（SSE 升级核心） ====================

    /**
     * 执行 ReAct 推理循环（流式版本，通过 SseEmitter 推送全流程给前端）
     *
     * @param messages     对话上下文
     * @param userQuestion 用户原始问题
     * @param context      推理上下文
     * @param emitter      SSE 推送器
     */
    public String executeStreaming(List<Message> messages, String userQuestion,
                                    ReActContext context, SseEmitter emitter) {
        List<ReActStep> reasoningChain = new ArrayList<>();
        String finalAnswer = null;
        AtomicBoolean disconnected = new AtomicBoolean(false);

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            if (disconnected.get()) break;
            log.info("=== ReAct 流式 第 {} 轮开始 | 用户问题: {} ===", i + 1, userQuestion);

            // ========== Step 1: Thought + Action 决策（流式） ==========
            ReActResponse response;
            try {
                response = deepSeekApiClient.reactThinkStream(messages, reasoningChain, token -> {
                    if (disconnected.get()) return;
                    try {
                        Map<String, Object> data = new HashMap<>();
                        data.put("step", "thought");
                        data.put("content", token);
                        emitter.send(SseEmitter.event().name("think").data(data));
                    } catch (IOException e) {
                        disconnected.set(true);
                    }
                });
            } catch (Exception e) {
                log.error("LLM 流式调用失败: {}", e.getMessage());
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(Map.of("message", "AI 服务调用失败")));
                } catch (IOException ignored) {}
                break;
            }

            if (disconnected.get()) break;

            if (response.getThought() == null) {
                log.error("LLM 未返回 Thought，终止循环");
                break;
            }

            log.info("Thought: {}", response.getThought());

            ReActStep step = new ReActStep();
            step.setIteration(i + 1);
            step.setThought(response.getThought());

            // ========== Step 2: 判断是否需要调用工具 ==========
            if (response.isFinished()) {
                finalAnswer = response.getFinalAnswer();
                step.setType(ReActStepType.FINISH);
                step.setFinalAnswer(finalAnswer);
                reasoningChain.add(step);

                log.info("Action: finish");
                log.info("最终答案: {}", finalAnswer);

                // 逐字推送 Final Answer（打字机效果）
                if (finalAnswer != null && !finalAnswer.isEmpty()) {
                    for (int j = 0; j < finalAnswer.length(); j++) {
                        if (disconnected.get()) break;
                        try {
                            Map<String, Object> data = new HashMap<>();
                            data.put("step", "answer");
                            data.put("content", String.valueOf(finalAnswer.charAt(j)));
                            emitter.send(SseEmitter.event().name("token").data(data));
                            Thread.sleep(30);
                        } catch (IOException e) {
                            disconnected.set(true);
                            break;
                        } catch (InterruptedException e) {
                            disconnected.set(true);
                            break;
                        }
                    }
                }
                break;
            }

            // ========== Step 3: 执行工具调用 ==========
            ToolAction action = response.getAction();
            log.info("Action: {}({})", action.getName(), action.getParameters());

            // 通知前端：开始调用工具
            try {
                Map<String, Object> toolData = new HashMap<>();
                toolData.put("step", "action");
                toolData.put("name", action.getName());
                toolData.put("args", action.getParameters());
                emitter.send(SseEmitter.event().name("tool").data(toolData));
            } catch (IOException e) {
                disconnected.set(true);
                break;
            }

            // 执行工具
            long toolStartTime = System.currentTimeMillis();
            String observation;
            try {
                Tool tool = toolRegistry.getTool(action.getName());
                observation = tool.execute(action.getParameters(), context);
                log.info("工具执行耗时: {}ms", System.currentTimeMillis() - toolStartTime);
            } catch (ToolNotFoundException e) {
                log.error("工具未找到: {}", e.getMessage());
                observation = "工具执行失败: " + e.getMessage();
            } catch (Exception e) {
                log.error("工具执行失败: {}", e.getMessage());
                observation = "工具执行失败: " + e.getMessage();
            }

            log.info("Observation: {}", observation);

            // 通知前端：工具返回结果
            try {
                Map<String, Object> obsData = new HashMap<>();
                obsData.put("step", "observation");
                obsData.put("content", observation);
                emitter.send(SseEmitter.event().name("obs").data(obsData));
            } catch (IOException e) {
                disconnected.set(true);
                break;
            }

            step.setType(ReActStepType.ACTION);
            step.setAction(action);
            step.setObservation(observation);
            reasoningChain.add(step);

            messages.add(new Message("assistant", formatReActContext(
                response.getThought(), action, observation
            )));

            log.info("=== ReAct 流式 第 {} 轮结束 ===", i + 1);
        }

        // 保存推理链（调试用）
        saveReasoningChain(userQuestion, reasoningChain);

        // 兜底处理
        if (finalAnswer == null && !disconnected.get()) {
            finalAnswer = generateFallbackAnswer(reasoningChain);
            log.warn("达到最大轮次 {}，给出兜底回答", MAX_ITERATIONS);
            if (finalAnswer != null && !finalAnswer.isEmpty()) {
                for (int j = 0; j < finalAnswer.length(); j++) {
                    if (disconnected.get()) break;
                    try {
                        Map<String, Object> data = new HashMap<>();
                        data.put("step", "answer");
                        data.put("content", String.valueOf(finalAnswer.charAt(j)));
                        emitter.send(SseEmitter.event().name("token").data(data));
                        Thread.sleep(30);
                    } catch (IOException e) {
                        disconnected.set(true);
                        break;
                    } catch (InterruptedException e) {
                        disconnected.set(true);
                        break;
                    }
                }
            }
        }

        return finalAnswer;
    }

    // ==================== 工具方法 ====================

    private String formatReActContext(String thought, ToolAction action, String observation) {
        return String.format(
            "Thought: %s\nAction: %s\nObservation: %s",
            thought,
            action.toString(),
            observation
        );
    }

    private void saveReasoningChain(String userQuestion, List<ReActStep> chain) {
        try {
            log.info("推理链记录 [问题: {}]:", userQuestion);
            for (ReActStep step : chain) {
                log.info("  轮次 {} [{}]: Thought={}, Action={}, Observation={}",
                    step.getIteration(),
                    step.getType(),
                    step.getThought(),
                    step.getAction() != null ? step.getAction().getName() : "N/A",
                    step.getObservation() != null ? "有" : "无"
                );
            }
        } catch (Exception e) {
            log.error("保存推理链失败", e);
        }
    }

    private String generateFallbackAnswer(List<ReActStep> chain) {
        return "我已经尝试了多种方法来分析您的问题，但可能需要更多信息来给出准确建议。" +
               "建议您提供更详细的宠物症状描述，或咨询专业兽医。";
    }
}

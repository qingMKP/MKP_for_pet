package com.mkp.chatbot.client;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.mkp.chatbot.service.react.Message;
import com.mkp.chatbot.service.react.ReActResponse;
import com.mkp.chatbot.service.react.ReActStep;
import com.mkp.chatbot.service.react.ToolAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DeepSeek API 客户端
 *
 * 职责：
 * 1. 封装与 DeepSeek API 的 HTTP 通信
 * 2. 提供 ReAct 格式的交互方法（同步 + 流式）
 * 3. 解析 LLM 的 ReAct 格式输出
 */
@Service
@Slf4j
public class DeepSeekApiClient {

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.api-url}")
    private String apiUrl;

    @Value("${deepseek.model}")
    private String model;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private WebClient webClient;

    // ==================== 同步调用（保留作为降级兜底） ====================

    /**
     * ReAct 思考：让 LLM 输出 Thought + Action（同步版本）
     */
    public ReActResponse reactThink(List<Message> messages, List<ReActStep> reasoningChain) {
        List<Map<String, Object>> reactMessages = buildReActMessages(messages, reasoningChain);
        String content = callApi(reactMessages);
        return parseReActResponse(content);
    }

    // ==================== 流式调用（SSE 升级核心） ====================

    /**
     * ReAct 思考：流式版本
     *
     * @param messages       当前对话上下文
     * @param reasoningChain 已完成的推理步骤
     * @param onToken        token 回调，每收到一个 delta.content 就触发
     * @return ReActResponse 包含 Thought、Action、FinalAnswer
     */
    public ReActResponse reactThinkStream(List<Message> messages, List<ReActStep> reasoningChain,
                                          Consumer<String> onToken) {
        List<Map<String, Object>> reactMessages = buildReActMessages(messages, reasoningChain);
        String content = callApiStreaming(reactMessages, onToken);
        return parseReActResponse(content);
    }

    // ==================== 消息构建（reactThink 和 reactThinkStream 共用） ====================

    private List<Map<String, Object>> buildReActMessages(List<Message> messages, List<ReActStep> reasoningChain) {
        List<Map<String, Object>> reactMessages = new ArrayList<>();

        for (Message msg : messages) {
            Map<String, Object> m = new HashMap<>();
            m.put("role", msg.getRole());
            m.put("content", msg.getContent());
            reactMessages.add(m);
        }

        if (!reasoningChain.isEmpty()) {
            StringBuilder chainContext = new StringBuilder();
            chainContext.append("【之前的推理过程】\n");
            for (ReActStep step : reasoningChain) {
                chainContext.append("轮次 ").append(step.getIteration()).append("\n");
                chainContext.append("Thought: ").append(step.getThought()).append("\n");
                if (step.getAction() != null) {
                    chainContext.append("Action: ").append(step.getAction().getName())
                                .append(step.getAction().getParameters()).append("\n");
                }
                if (step.getObservation() != null) {
                    chainContext.append("Observation: ").append(step.getObservation()).append("\n");
                }
                chainContext.append("\n");
            }
            chainContext.append("【请继续下一轮思考】\n");

            Map<String, Object> chainMsg = new HashMap<>();
            chainMsg.put("role", "user");
            chainMsg.put("content", chainContext.toString());
            reactMessages.add(chainMsg);
        }

        Map<String, Object> instructMsg = new HashMap<>();
        instructMsg.put("role", "user");
        instructMsg.put("content",
            "请分析当前情况，严格按照以下格式回复（不要添加其他内容）：\n\n" +
            "Thought: [你的详细推理过程，说明为什么需要工具或为什么可以直接回答]\n" +
            "Action: [工具名称]{\"参数名\": \"参数值\"}\n" +
            "或\n" +
            "Action: finish\n" +
            "Final Answer: [给用户的最终回答，仅当 Action 为 finish 时填写]\n\n" +
            "注意：\n" +
            "- Thought 必须详细说明推理过程\n" +
            "- 如果需要工具，Action 必须包含工具名称和参数\n" +
            "- 如果不需要工具，Action 必须是 finish\n" +
            "- Final Answer 只在 Action 为 finish 时填写"
        );
        reactMessages.add(instructMsg);

        return reactMessages;
    }

    // ==================== 同步 API 调用 ====================

    private String callApi(List<Map<String, Object>> messages) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", 0.3);
        body.put("max_tokens", 2000);

        HttpEntity<String> request = new HttpEntity<>(JSONUtil.toJsonStr(body), headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                apiUrl, HttpMethod.POST, request, String.class
            );

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new RuntimeException("API 返回异常状态码: " + response.getStatusCode());
            }

            JSONObject json = JSONUtil.parseObj(response.getBody());
            JSONArray choices = json.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("API 返回空 choices");
            }

            JSONObject first = choices.getJSONObject(0);
            JSONObject message = first.getJSONObject("message");
            if (message == null) {
                throw new RuntimeException("API 返回空 message");
            }

            String result = message.getStr("content");
            log.debug("DeepSeek 原始返回: {}", result);
            return result;

        } catch (Exception e) {
            log.error("DeepSeek API 调用失败", e);
            throw new RuntimeException("AI 服务调用失败: " + e.getMessage(), e);
        }
    }

    // ==================== 流式 API 调用 ====================

    /**
     * 使用 WebClient 消费 DeepSeek 的 SSE 流式响应
     *
     * @param messages API 请求消息列表
     * @param onToken  每收到一个 token 就回调（用于推送给前端 SSE）
     * @return LLM 返回的完整文本内容
     */
    private String callApiStreaming(List<Map<String, Object>> messages, Consumer<String> onToken) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", 0.3);
        body.put("max_tokens", 2000);
        body.put("stream", true);

        StringBuilder fullContent = new StringBuilder();

        try {
            log.info("开始 DeepSeek 流式调用, model={}", model);
            webClient.post()
                    .uri(apiUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                    .filter(sse -> sse.data() != null)
                    .doOnNext(sse -> {
                        String data = sse.data();
                        log.debug("SSE event: data长度={}", data.length());
                        if ("[DONE]".equals(data)) {
                            return;
                        }
                        try {
                            JSONObject json = JSONUtil.parseObj(data);
                            JSONArray choices = json.getJSONArray("choices");
                            if (choices == null || choices.isEmpty()) {
                                return;
                            }
                            JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
                            if (delta == null) {
                                return;
                            }
                            String content = delta.getStr("content");
                            if (content != null && !content.isEmpty()) {
                                fullContent.append(content);
                                if (onToken != null) {
                                    onToken.accept(content);
                                }
                            }
                        } catch (Exception e) {
                            log.debug("SSE 数据解析失败: {}", data.length() > 100 ? data.substring(0, 100) + "..." : data);
                        }
                    })
                    .blockLast();

            String result = fullContent.toString();
            log.info("DeepSeek 流式返回完成, 内容长度={}, 内容预览={}",
                    result.length(),
                    result.length() > 200 ? result.substring(0, 200) + "..." : result);
            return result;

        } catch (Exception e) {
            log.error("DeepSeek 流式 API 调用失败", e);
            throw new RuntimeException("AI 服务调用失败: " + e.getMessage(), e);
        }
    }

    // ==================== ReAct 响应解析（同步和流式共用） ====================

    private ReActResponse parseReActResponse(String content) {
        ReActResponse result = new ReActResponse();

        if (content == null || content.trim().isEmpty()) {
            log.error("LLM 返回空内容");
            return result;
        }

        log.debug("解析 ReAct 响应:\n{}", content);

        // 提取 Thought
        Pattern thoughtPattern = Pattern.compile(
            "Thought:\\s*(.+?)(?=\\nAction:|\\nFinal Answer:|$)",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );
        Matcher thoughtMatcher = thoughtPattern.matcher(content);
        if (thoughtMatcher.find()) {
            result.setThought(thoughtMatcher.group(1).trim());
        } else {
            log.warn("未解析到 Thought，使用默认提示");
            result.setThought("未获取到明确推理过程");
        }

        // 提取 Action
        Pattern actionPattern = Pattern.compile(
            "Action:\\s*(\\w+)(?:\\s*\\{([^}]*)\\})?",
            Pattern.CASE_INSENSITIVE
        );
        Matcher actionMatcher = actionPattern.matcher(content);

        if (actionMatcher.find()) {
            String actionName = actionMatcher.group(1).trim();

            if ("finish".equalsIgnoreCase(actionName)) {
                Pattern finalPattern = Pattern.compile(
                    "Final Answer:\\s*(.+)",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE
                );
                Matcher finalMatcher = finalPattern.matcher(content);
                if (finalMatcher.find()) {
                    result.setFinalAnswer(finalMatcher.group(1).trim());
                } else {
                    result.setFinalAnswer(result.getThought());
                }

                ToolAction finishAction = new ToolAction();
                finishAction.setName("finish");
                finishAction.setParameters(new HashMap<>());
                result.setAction(finishAction);

            } else {
                ToolAction action = new ToolAction();
                action.setName(actionName);

                Map<String, Object> params = new HashMap<>();
                if (actionMatcher.group(2) != null) {
                    parseParams(actionMatcher.group(2).trim(), params);
                }
                action.setParameters(params);
                result.setAction(action);
            }
        } else {
            log.warn("未解析到 Action，默认 finish");
            result.setFinalAnswer(result.getThought());
            ToolAction finishAction = new ToolAction();
            finishAction.setName("finish");
            finishAction.setParameters(new HashMap<>());
            result.setAction(finishAction);
        }

        return result;
    }

    /**
     * 解析工具参数，兼容 JSON 和非 JSON 两种格式
     * JSON:    {"keyword": "柯基"}
     * 非JSON:  keyword=柯基
     */
    private void parseParams(String raw, Map<String, Object> params) {
        if (raw.isEmpty()) return;

        // 尝试标准 JSON
        String jsonStr = "{" + raw + "}";
        try {
            JSONObject json = JSONUtil.parseObj(jsonStr);
            if (!json.isEmpty()) {
                for (String key : json.keySet()) {
                    params.put(key, json.get(key));
                }
                return;
            }
        } catch (Exception ignored) {
            // 不是 JSON，fallthrough 到 key=value 解析
        }

        // 回退：key=value 格式
        int eqIdx = raw.indexOf('=');
        if (eqIdx > 0) {
            String key = raw.substring(0, eqIdx).trim();
            String value = raw.substring(eqIdx + 1).trim();
            if (value.length() >= 2 &&
                ((value.startsWith("\"") && value.endsWith("\"")) ||
                 (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            if (!key.isEmpty()) {
                params.put(key, value);
                return;
            }
        }

        log.warn("参数解析失败: {}", raw);
    }
}

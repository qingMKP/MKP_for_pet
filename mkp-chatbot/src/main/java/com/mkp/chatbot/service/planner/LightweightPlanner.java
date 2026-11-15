package com.mkp.chatbot.service.planner;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.mkp.chatbot.service.router.IntentRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 轻量任务规划器。
 *
 * 只在意图路由判定为 MIXED（一个问题里混了多类诉求）时启用：
 * 先把问题拆成 1-3 个可独立检索的子任务，每个子任务只带一类工具执行，
 * 最后用一次流式调用把各步结果汇总成面向用户的回答。
 *
 * 指代处理放在规划阶段：结合对话历史把"它/那个"还原成 subject，
 * 子任务的检索对象由 subject + query 拼成，不依赖子步骤之间传上下文。
 */
@Slf4j
@Component
public class LightweightPlanner {

    /**
     * 一个子任务：查什么（intent）、查谁（subject）、用什么词查（query）。
     */
    public record Step(IntentRouter.Intent intent, String subject, String query) {
    }

    /**
     * usable() = 至少拆出 2 步；否则说明问题本质是单意图，交给单轮链路处理。
     */
    public record Plan(List<Step> steps, String source, long costMs) {
        public boolean usable() {
            return steps != null && steps.size() >= 2;
        }
    }

    private static final int MAX_STEPS = 3;
    private static final int HISTORY_LIMIT = 4;
    private static final int HISTORY_TEXT_LIMIT = 200;
    private static final long PLAN_TIMEOUT_MS = 2000L;

    private static final String SYSTEM_PROMPT = """
            你是任务规划器。结合对话历史（可能为空）理解用户问题里的指代，把问题拆成 1-3 个可以独立检索的子任务。
            只输出 JSON 数组，不要解释，不要 markdown 代码块。每个元素形如：
            {"intent":"knowledge","subject":"柯基","query":"拉稀 原因"}
            intent 只能取：
            knowledge - 宠物知识、疾病、喂养、护理
            community - 站内帖子、经验分享、社区讨论
            market - 寄养、领养、出售、价格
            subject 是这一步要检索的对象（品种、城市、具体事物），必须是具体名词，禁止出现 他、她、它、这个、那个。
            query 是这一步要查的内容，用简短关键词，不要整句复述用户问题。
            如果用户问题里有指代，先用对话历史或同一句话的前半段把指代还原成具体对象，再写进 subject。
            同一个 intent 只输出一次，不要编造用户没有提到的需求。""";

    private final ChatClient chatClient;
    private final boolean enabled;

    public LightweightPlanner(ChatClient chatClient,
                              @Value("${mkp.chatbot.planner.enabled:true}") boolean enabled) {
        this.chatClient = chatClient;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @param history 会话历史（旧 -> 新），只取最近几条用于消解指代，可为空
     */
    public Plan plan(String question, List<Map<String, String>> history) {
        long start = System.currentTimeMillis();
        if (!enabled || question == null || question.isBlank()) {
            return new Plan(List.of(), "disabled", 0L);
        }
        try {
            String output = CompletableFuture.supplyAsync(() -> askPlanner(question, history))
                    .orTimeout(PLAN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .join();
            List<Step> steps = parseSteps(output);
            return new Plan(steps, "llm", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("任务规划失败，回退单轮链路: {}", e.toString());
            return new Plan(List.of(), "fallback", System.currentTimeMillis() - start);
        }
    }

    private String askPlanner(String question, List<Map<String, String>> history) {
        StringBuilder user = new StringBuilder();
        if (history != null && !history.isEmpty()) {
            user.append("对话历史（从旧到新，最多最近 ").append(HISTORY_LIMIT).append(" 条）：\n");
            int from = Math.max(0, history.size() - HISTORY_LIMIT);
            for (int i = from; i < history.size(); i++) {
                Map<String, String> item = history.get(i);
                String content = item == null ? null : item.get("content");
                if (content == null || content.isBlank()) {
                    continue;
                }
                String role = item.get("role");
                user.append("- ").append("assistant".equalsIgnoreCase(role) ? "助手" : "用户").append("：")
                        .append(content.length() > HISTORY_TEXT_LIMIT
                                ? content.substring(0, HISTORY_TEXT_LIMIT) : content)
                        .append("\n");
            }
            user.append("\n");
        }
        user.append("用户本轮问题：").append(question);

        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(user.toString())
                .options(ChatOptions.builder().temperature(0.0).maxTokens(300).build())
                .call()
                .content();
    }

    static List<Step> parseSteps(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return List.of();
        }
        try {
            JSONArray array = JSONUtil.parseArray(raw.substring(start, end + 1));
            Set<String> seen = new LinkedHashSet<>();
            List<Step> steps = new ArrayList<>();
            for (int i = 0; i < array.size() && steps.size() < MAX_STEPS; i++) {
                JSONObject item = array.getJSONObject(i);
                if (item == null) {
                    continue;
                }
                IntentRouter.Intent intent = IntentRouter.fromLabel(item.getStr("intent"));
                String subject = trimToEmpty(item.getStr("subject"));
                String query = trimToEmpty(item.getStr("query"));
                if (intent == IntentRouter.Intent.MIXED || intent == IntentRouter.Intent.CHITCHAT) {
                    continue;
                }
                // subject 和 query 都空的步骤没有任何检索价值
                if (subject.isEmpty() && query.isEmpty()) {
                    continue;
                }
                if (seen.add(intent.name() + "|" + subject + "|" + query)) {
                    steps.add(new Step(intent, subject, query));
                }
            }
            return steps;
        } catch (Exception e) {
            log.warn("规划结果解析失败: {}", e.toString());
            return List.of();
        }
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
package com.mkp.chatbot.service.router;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 意图路由。
 *
 * 分类发生在调用主模型之前，只决定本次请求挂载哪些工具，不改变 SSE 契约。
 * 策略：关键词规则优先（0 延迟、0 成本），规则未命中时用小模型分类兜底；
 * 任何异常或超时统一降级为 MIXED（等价于改造前的“全量挂载”）。
 */
@Slf4j
@Component
public class IntentRouter {

    public enum Intent {
        KNOWLEDGE(Set.of("search_knowledge")),
        COMMUNITY(Set.of("search_posts")),
        MARKET(Set.of("search_market_posts")),
        MIXED(Set.of("search_knowledge", "search_posts", "search_market_posts")),
        CHITCHAT(Set.of());

        private final Set<String> toolNames;

        Intent(Set<String> toolNames) {
            this.toolNames = toolNames;
        }

        public Set<String> toolNames() {
            return toolNames;
        }
    }

    public record RoutingResult(Intent intent, String source, long costMs) {
    }

    private static final Map<Intent, List<String>> RULES = new LinkedHashMap<>();

    static {
        RULES.put(Intent.MARKET, List.of("寄养", "领养", "出售", "转让", "多少钱", "价格", "同城", "买一只", "收一只"));
        RULES.put(Intent.COMMUNITY, List.of("帖子", "社区", "大家", "有没有人", "分享", "经验", "案例", "评论"));
        RULES.put(Intent.KNOWLEDGE, List.of("怎么", "如何", "能吃", "不能吃", "症状", "疫苗", "驱虫", "喂养",
                "护理", "生病", "呕吐", "拉稀", "掉毛", "训练", "绝育", "驱虫药"));
        RULES.put(Intent.CHITCHAT, List.of("你好", "您好", "谢谢", "在吗", "你是谁", "再见", "早上好", "晚上好"));
    }

    private static final String SYSTEM_PROMPT = """
            你是意图分类器。把用户问题归为以下之一，只输出一个英文单词，不要解释、不要标点：
            knowledge - 宠物养护、疾病、喂养、护理等专业知识
            community - 站内帖子、经验分享、社区讨论
            market - 寄养、领养、出售、购买、价格
            mixed - 同时涉及两类以上
            chitchat - 打招呼、感谢、闲聊""";

    private static final long CLASSIFY_TIMEOUT_MS = 1500L;

    private final ChatClient chatClient;
    private final boolean enabled;
    private final boolean llmEnabled;

    public IntentRouter(ChatClient chatClient,
                        @Value("${mkp.chatbot.router.enabled:true}") boolean enabled,
                        @Value("${mkp.chatbot.router.llm-enabled:true}") boolean llmEnabled) {
        this.chatClient = chatClient;
        this.enabled = enabled;
        this.llmEnabled = llmEnabled;
    }

    public RoutingResult route(String question) {
        long start = System.currentTimeMillis();
        if (!enabled || question == null || question.isBlank()) {
            return new RoutingResult(Intent.MIXED, "disabled", 0L);
        }

        Set<Intent> hits = new LinkedHashSet<>();
        RULES.forEach((intent, words) -> {
            for (String word : words) {
                if (question.contains(word)) {
                    hits.add(intent);
                    return;
                }
            }
        });

        // “你好，帮我找下寄养”这类问候+正事，不把问候算作一个意图
        if (hits.size() > 1) {
            hits.remove(Intent.CHITCHAT);
        }
        if (hits.size() > 1) {
            return new RoutingResult(Intent.MIXED, "rule", System.currentTimeMillis() - start);
        }
        if (hits.size() == 1) {
            return new RoutingResult(hits.iterator().next(), "rule", System.currentTimeMillis() - start);
        }

        if (!llmEnabled) {
            return new RoutingResult(Intent.MIXED, "rule-miss", System.currentTimeMillis() - start);
        }

        try {
            String output = CompletableFuture.supplyAsync(() -> classifyByLlm(question))
                    .orTimeout(CLASSIFY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .join();
            Intent intent = parse(output);
            return new RoutingResult(intent, "llm", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("意图分类失败，降级为 MIXED: {}", e.toString());
            return new RoutingResult(Intent.MIXED, "fallback", System.currentTimeMillis() - start);
        }
    }

    private String classifyByLlm(String question) {
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(question)
                .options(ChatOptions.builder().temperature(0.0).maxTokens(8).build())
                .call()
                .content();
    }

    static Intent parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Intent.MIXED;
        }
        String text = raw.trim().toLowerCase(Locale.ROOT);
        if (text.contains("mixed") || text.contains("混合")) {
            return Intent.MIXED;
        }
        if (text.contains("knowledge") || text.contains("知识")) {
            return Intent.KNOWLEDGE;
        }
        if (text.contains("community") || text.contains("社区")) {
            return Intent.COMMUNITY;
        }
        if (text.contains("market") || text.contains("寄养") || text.contains("交易") || text.contains("购买")) {
            return Intent.MARKET;
        }
        if (text.contains("chitchat") || text.contains("闲聊")) {
            return Intent.CHITCHAT;
        }
        return Intent.MIXED;
    }
}
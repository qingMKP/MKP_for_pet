package com.mkp.chatbot.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mkp.chatbot.mapper.ChatbotConversationMapper;
import com.mkp.chatbot.mapper.ChatbotMessageMapper;
import com.mkp.chatbot.mapper.ChatbotKnowledgeMapper;
import com.mkp.chatbot.service.ChatbotService;
import com.mkp.chatbot.service.prompt.PromptTemplateService;
import com.mkp.chatbot.service.planner.LightweightPlanner;
import com.mkp.chatbot.service.planner.LightweightPlanner.Plan;
import com.mkp.chatbot.service.planner.LightweightPlanner.Step;
import com.mkp.chatbot.service.planner.Scratchpad;
import com.mkp.chatbot.service.planner.StepResult;
import com.mkp.chatbot.service.react.ReActContext;
import com.mkp.chatbot.service.router.IntentRouter;
import com.mkp.chatbot.service.react.ToolRegistry;
import com.mkp.chatbot.service.tool.ChatbotTools;
import com.mkp.chatbot.service.tool.ObservableToolCallback;
import com.mkp.chatbot.service.vector.KnowledgeVectorSyncService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import com.mkp.common.dto.ChatbotReplyVO;
import com.mkp.common.dto.ChatbotPostVO;
import com.mkp.common.dto.KnowledgeSaveDTO;
import com.mkp.common.dto.PostingVO;
import com.mkp.common.entity.ChatbotConversation;
import com.mkp.common.entity.ChatbotKnowledge;
import com.mkp.common.entity.ChatbotMessage;
import com.mkp.common.result.R;
import com.mkp.common.util.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ChatbotServiceImpl implements ChatbotService {

    /**
     * 子任务执行器的系统提示。
     *
     * 要求它先调工具检索、再输出结构化 JSON：answer 给用户看，facts 给后续步骤用。
     * 解析失败会退化成纯文本（见 StepResult.fromRaw），不会中断链路。
     */
    private static final String STEP_SYSTEM_PROMPT = """
            你是多步任务中的一步执行器，只负责完成当前子任务，不要回答用户的其他问题，不要寒暄。
            需要事实依据时，先用可用工具检索；拿到结果后再总结。
            最终只输出一个 JSON 对象，不要输出 JSON 以外的任何内容，也不要加 markdown 代码块：
            {"answer":"这一步的简短结论，2-3 句","facts":{"品种":"柯基","城市":"广州"}}
            facts 只放后续步骤可能需要的关键事实，最多 5 条，键名用中文名词，值不超过 30 字；
            没有可复用的事实时输出空对象 {}。""";
    private static final Pattern PRONOUN_PATTERN =
            Pattern.compile("(^|[\\s，,。；;、])(他|她|它|这个|那个|该)");

    private final ChatbotKnowledgeMapper knowledgeMapper;
    private final ChatbotConversationMapper conversationMapper;
    private final ChatbotMessageMapper messageMapper;

    @Autowired
    private ChatMemory chatMemory;

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private ChatbotTools chatbotTools;

    @Autowired
    private IntentRouter intentRouter;

    @Autowired
    private LightweightPlanner lightweightPlanner;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private PromptTemplateService promptTemplateService;

    @Value("${mkp.chatbot.prompt.scene:default}")
    private String defaultPromptScene;

    @Autowired
    private KnowledgeVectorSyncService knowledgeVectorSyncService;

    public ChatbotServiceImpl(ChatbotKnowledgeMapper knowledgeMapper,
                              ChatbotConversationMapper conversationMapper,
                              ChatbotMessageMapper messageMapper) {
        this.knowledgeMapper = knowledgeMapper;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    // ==================== 核心问答（同步版） ====================

    @Override
    public R<ChatbotReplyVO> ask(String question, Long conversationId) {
        if (question == null || question.isBlank()) {
            return R.fail("Please enter a question");
        }

        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail("User not authenticated");
        }

        long startTime = System.currentTimeMillis();
        log.info("用户 {} 提问: {}", userId, question);

        // 第一步：自动创建或获取会话
        if (conversationId == null) {
            ChatbotConversation conv = new ChatbotConversation();
            conv.setUserId(userId);
            String title = question.length() > 50 ? question.substring(0, 50) : question;
            conv.setTitle(title);
            conversationMapper.insert(conv);
            conversationId = conv.getId();
        }

        // 第二步：历史由 Spring AI ChatMemory 管理，调用时经 advisor 自动注入

        // 第四步：意图路由 + Spring AI 调用，工具由模型原生 function calling 驱动
        // 路由器只裁剪本次挂载的工具集合；分类失败或超时统一降级 MIXED（等价于改造前的全量挂载）
        IntentRouter.RoutingResult routing = intentRouter.route(question);
        log.info("意图路由：intent={} source={} cost={}ms question={}",
                routing.intent(), routing.source(), routing.costMs(), question);

        ReActContext context = new ReActContext();
        String conversationKey = String.valueOf(conversationId);
        String answer;
        try {
            answer = chatClient.prompt()
                    .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationKey))
                    .system(buildSystemPrompt(routing.intent().toolNames()))
                    .user(question)
                    .toolCallbacks(filterToolCallbacks(routing.intent()))
                    .toolContext(Map.of("mkpContext", context))
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("Spring AI 调用失败，降级处理", e);
            answer = "抱歉，我暂时无法回答您的问题，请稍后再试。";
        }

        if (answer == null || answer.isBlank()) {
            answer = "抱歉，我暂时无法回答您的问题，请稍后再试。";
        }

        // 第五步：本轮问答由 ChatMemoryAdvisor 在调用结束时自动写回

        // 第六步：写入 MySQL（持久化）
        saveToMySQL(conversationId, question, answer, context.getRelatedPosts(), context.getRelatedPostings());

        long costTime = System.currentTimeMillis() - startTime;
        log.info("回答完成，耗时: {}ms", costTime);

        // 第七步：构建响应（包含推理过程中收集的相关帖子）
        ChatbotReplyVO reply = new ChatbotReplyVO();
        reply.setContent(answer);
        reply.setPosts(context.getRelatedPosts());
        reply.setPostings(context.getRelatedPostings());
        reply.setConversationId(conversationId);
        return R.ok(reply);
    }

    // ==================== 核心问答（SSE 流式版本） ====================

    @Override
    public void askStream(String question, Long conversationId, SseEmitter emitter) {
        if (question == null || question.isBlank()) {
            try {
                emitter.send(SseEmitter.event().name("error")
                        .data(Map.of("message", "请输入问题")));
                emitter.complete();
            } catch (IOException ignored) {}
            return;
        }

        Long userId = UserContext.getUserId();
        if (userId == null) {
            try {
                emitter.send(SseEmitter.event().name("error")
                        .data(Map.of("message", "用户未认证")));
                emitter.complete();
            } catch (IOException ignored) {}
            return;
        }

        log.info("用户 {} 流式提问: {}", userId, question);

        try {
            // 第一步：自动创建或获取会话（复用）
            if (conversationId == null) {
                ChatbotConversation conv = new ChatbotConversation();
                conv.setUserId(userId);
                String title = question.length() > 50 ? question.substring(0, 50) : question;
                conv.setTitle(title);
                conversationMapper.insert(conv);
                conversationId = conv.getId();
            }

            // 第二步：规划器需要的历史从 ChatMemory 读，主回答的历史由 advisor 自动注入
            List<Map<String, String>> history = historyForPlanner(conversationId);

            // 第四步：Spring AI 流式调用，工具由模型原生 function calling 驱动
            ReActContext context = new ReActContext();

            // 先分类，再按意图挂载工具：只影响工具集合，SSE 事件名与结构保持不变
            IntentRouter.RoutingResult routing = intentRouter.route(question);
            log.info("意图路由：intent={} source={} cost={}ms question={}",
                    routing.intent(), routing.source(), routing.costMs(), question);
            sendEvent(emitter, "think", Map.of("content", "正在分析问题…"));

            // 多意图才做全局规划：规划 → 分步执行 → 一次性流式汇总；规划不可用则回退单轮
            String answer = null;
            Plan plan = buildPlan(routing, question, history, emitter);
            if (plan != null) {
                answer = executePlan(plan, question, conversationId, context, emitter);
            }
            if (answer == null) {
                answer = streamAnswer(question,
                        buildSystemPrompt(routing.intent().toolNames()),
                        observableCallbacks(routing.intent(), emitter),
                        context, emitter, conversationId);
            }
            if (answer == null) {
                emitter.complete();
                return;
            }

            // 第五步：本轮问答由 ChatMemoryAdvisor 自动写回
            if (answer == null || answer.isBlank()) {
                answer = "抱歉，我暂时无法回答您的问题，请稍后再试。";
            }

            // 第六步：写入 MySQL 持久化（复用）
            saveToMySQL(conversationId, question, answer, context.getRelatedPosts(), context.getRelatedPostings());

            // 第七步：发送 done 事件
            Map<String, Object> doneData = new HashMap<>();
            doneData.put("conversationId", conversationId);
            doneData.put("posts", context.getRelatedPosts() != null ? context.getRelatedPosts() : List.of());
            doneData.put("postings", context.getRelatedPostings() != null ? context.getRelatedPostings() : List.of());
            emitter.send(SseEmitter.event().name("done").data(doneData));
            emitter.complete();

        } catch (IOException e) {
            log.info("SSE 连接被客户端断开");
        } catch (Exception e) {
            log.error("SSE 流式问答异常", e);
            try {
                emitter.send(SseEmitter.event().name("error")
                        .data(Map.of("message", "服务异常，请稍后重试")));
                emitter.completeWithError(e);
            } catch (IOException ignored) {}
        }
    }

    /**
     * 构建 System Prompt（模板化版本）
     *
     * 改造点：
     * 1. 从 PromptTemplateService 加载模板，替代硬编码常量
     * 2. 工具描述拼接逻辑独立为方法，职责更清晰
     * 3. 支持通过配置切换场景
     */
    private String buildSystemPrompt(Set<String> toolNames) {
        // 1. 加载模板内容（带缓存）
        String template = promptTemplateService.getTemplateContent(defaultPromptScene);

        // 兜底：如果数据库无模板，使用内置极简模板（保证服务不挂）
        if (template == null || template.isBlank()) {
            log.error("Prompt 模板加载失败，使用内置兜底模板: scene={}", defaultPromptScene);
            template = buildFallbackTemplate();
        }

        // 2. 动态注入工具描述（只描述本轮真正挂载的工具，避免模型去调不存在的工具）
        String toolsDesc = buildToolsDescription(toolNames);

        // 3. 替换占位符
        return template.replace("{tools_description}", toolsDesc);
    }

    /**
     * 构建工具描述字符串（从 ToolRegistry 动态获取，按意图裁剪）
     */
    private String buildToolsDescription(Set<String> toolNames) {
        StringBuilder toolsDesc = new StringBuilder();

        toolRegistry.getAllTools().forEach(tool -> {
            if (toolNames != null && !toolNames.contains(tool.getName())) {
                return;
            }
            toolsDesc.append("- ").append(tool.getName())
                     .append(": ").append(tool.getDescription())
                     .append("\n");

            // 添加参数说明
            if (tool.getParametersSchema() != null && !tool.getParametersSchema().isEmpty()) {
                toolsDesc.append("  参数:\n");
                tool.getParametersSchema().forEach((param, desc) -> {
                    toolsDesc.append("    - ").append(param).append(": ").append(desc).append("\n");
                });
            }
            toolsDesc.append("\n");
        });

        return toolsDesc.toString();
    }

    /**
     * 只有多意图才值得做全局规划；单意图、规划失败、拆不出 2 步都返回 null，走原来的单轮链路。
     */
    private Plan buildPlan(IntentRouter.RoutingResult routing, String question,
                                              List<Map<String, String>> history, SseEmitter emitter) {
        if (routing.intent() != IntentRouter.Intent.MIXED || !lightweightPlanner.isEnabled()) {
            return null;
        }
        Plan plan = lightweightPlanner.plan(question, history);
        log.info("任务规划：steps={} source={} cost={}ms question={}",
                plan.steps().size(), plan.source(), plan.costMs(), question);
        if (!plan.usable()) {
            return null;
        }
        StringBuilder summary = new StringBuilder("已拆解为 ").append(plan.steps().size()).append(" 个步骤：");
        for (int i = 0; i < plan.steps().size(); i++) {
            Step step = plan.steps().get(i);
            if (i > 0) {
                summary.append("；");
            }
            summary.append(intentLabel(step.intent())).append(" - ").append(stepText(step, question));
        }
        sendEvent(emitter, "think", Map.of("content", summary.toString()));
        return plan;
    }

    /**
     * 按计划逐个子任务执行（每个子任务只带一类工具），最后把各步结果一次性汇总。
     */
    private String executePlan(Plan plan, String question, Long conversationId,
                               ReActContext context, SseEmitter emitter) {
        StringBuilder evidence = new StringBuilder();
        // 结构化共享区：上游步骤产出的事实，供下游步骤检索时参考
        Scratchpad scratchpad = new Scratchpad();
        int index = 1;
        for (Step step : plan.steps()) {
            sendEvent(emitter, "think", Map.of("content",
                    "第" + index + "步：" + intentLabel(step.intent()) + " - " + stepText(step, question)));
            String stateBlock = scratchpad.toPromptBlock();
            if (!stateBlock.isBlank()) {
                log.info("第{}步携带已知信息: {}", index, stateBlock.replace("\n", " "));
            }
            StepResult result = runStep(step, question, stateBlock, context, emitter);
            if (result.answer() != null && !result.answer().isBlank()) {
                evidence.append("【").append(intentLabel(step.intent())).append("】")
                        .append(stepText(step, question)).append("\n")
                        .append(result.answer().trim()).append("\n\n");
            }
            if (result.facts() != null && !result.facts().isEmpty()) {
                scratchpad.merge(result.facts());
                log.info("第{}步结构化产出: {}", index, result.facts());
            }
            scratchpad.addTrace(intentLabel(step.intent()), result.answer());
            index++;
        }
        return streamAnswer(question, buildSynthesisPrompt(evidence.toString(), scratchpad),
                List.of(), context, emitter, conversationId);
    }

    /**
     * 执行单个子任务：非流式调用，只挂该子任务对应的一类工具，工具事件照常推给前端。
     */
    private StepResult runStep(Step step, String question, String stateBlock,
                               ReActContext context, SseEmitter emitter) {
        String task = stepText(step, question);
        String userText = stateBlock.isBlank() ? "当前子任务：" + task : stateBlock + "\n当前子任务：" + task;
        try {
            String raw = chatClient.prompt()
                    .system(STEP_SYSTEM_PROMPT)
                    .user(userText)
                    .toolCallbacks(observableCallbacks(step.intent(), emitter))
                    .toolContext(Map.of("mkpContext", context))
                    .call()
                    .content();
            return StepResult.fromRaw(raw);
        } catch (Exception e) {
            log.warn("子任务执行失败: intent={} step={} error={}", step.intent(), task, e.toString());
            return StepResult.empty();
        }
    }

    /**
     * 流式执行一次问答，token 事件逐段推送。返回 null 表示已向客户端发过 error 事件。
     */
    private String streamAnswer(String question, String systemPrompt,
                                List<ToolCallback> toolCallbacks, ReActContext context, SseEmitter emitter,
                                Long conversationId) {
        StringBuilder answerBuffer = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        chatClient.prompt()
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, String.valueOf(conversationId)))
                .system(systemPrompt)
                .user(question)
                .toolCallbacks(toolCallbacks)
                .toolContext(Map.of("mkpContext", context))
                .stream()
                .chatResponse()
                .subscribe(response -> {
                    if (response == null || response.getResult() == null) {
                        return;
                    }
                    AssistantMessage output = response.getResult().getOutput();
                    String text = output.getText();
                    if (text != null && !text.isEmpty()) {
                        answerBuffer.append(text);
                        sendEvent(emitter, "token", Map.of("step", "answer", "content", text));
                    }
                }, error -> {
                    errorRef.set(error);
                    latch.countDown();
                }, latch::countDown);

        try {
            if (!latch.await(120, TimeUnit.SECONDS)) {
                log.warn("Spring AI 流式调用超时");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (errorRef.get() != null) {
            log.error("Spring AI 流式调用失败", errorRef.get());
            sendEvent(emitter, "error", Map.of("message", "AI 服务调用失败"));
            return null;
        }
        return answerBuffer.toString();
    }

    /**
     * 按意图取工具并包上 SSE 观察器，工具执行前后各推一次 tool 事件。
     */
    private List<ToolCallback> observableCallbacks(IntentRouter.Intent intent, SseEmitter emitter) {
        List<ToolCallback> callbacks = new ArrayList<>();
        for (ToolCallback callback : filterToolCallbacks(intent)) {
            callbacks.add(new ObservableToolCallback(callback, (phase, payload) -> {
                if ("action".equals(phase)) {
                    sendEvent(emitter, "tool", Map.of(
                            "step", "action",
                            "name", callback.getToolDefinition().name(),
                            "args", payload));
                } else {
                    sendEvent(emitter, "tool", Map.of(
                            "step", "observation",
                            "content", payload));
                }
            }));
        }
        return callbacks;
    }

    /**
     * 汇总阶段的 System Prompt：不挂工具，只要求基于检索结果作答。
     */
    private String buildSynthesisPrompt(String evidence, Scratchpad scratchpad) {
        String base = buildSystemPrompt(Set.of());
        String facts = scratchpad == null ? "" : scratchpad.toPromptBlock();
        return base + "\n\n本轮已完成检索，结果如下（可能为空）：\n"
                + (evidence.isBlank() ? "（无检索结果）" : evidence)
                + (facts.isBlank() ? "" : "\n关键事实：\n" + facts)
                + "\n请只依据以上检索结果和常识回答用户，本轮不需要调用任何工具；没有检索到的内容要如实说明。";
    }

    private String intentLabel(IntentRouter.Intent intent) {
        return switch (intent) {
            case KNOWLEDGE -> "知识库";
            case COMMUNITY -> "社区帖子";
            case MARKET -> "交易广场";
            case MIXED -> "综合";
            case CHITCHAT -> "闲聊";
        };
    }
    /**
     * 把子任务拼成可以直接检索的文本。
     *
     * 有 subject 时由 subject 兜住指代，query 里出现"它"也无所谓；
     * 没有 subject 且 query 里还带代词，才是真的没法消解，退回原问题当检索输入。
     */
    private String stepText(Step step, String question) {
        String subject = step.subject() == null ? "" : step.subject().trim();
        String query = step.query() == null ? "" : step.query().trim();
        if (!subject.isEmpty()) {
            return query.isEmpty() ? subject : subject + " " + query;
        }
        if (query.isEmpty() || PRONOUN_PATTERN.matcher(query).find()) {
            return question;
        }
        return query;
    }
    /**
     * 把 @Tool 方法转成 ToolCallback，并按路由结果裁剪。
     * CHITCHAT 这类不挂工具的意图返回空列表（不能用 null，Spring AI 内部会直接遍历）。
     */
    private List<ToolCallback> filterToolCallbacks(IntentRouter.Intent intent) {
        Set<String> names = intent == null ? null : intent.toolNames();
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(ToolCallbacks.from(chatbotTools))
                .filter(callback -> names.contains(callback.getToolDefinition().name()))
                .toList();
    }

    /**
     * 兜底 Prompt 模板（极简版，保证数据库异常时服务可用）
     */
    private String buildFallbackTemplate() {
        return "你是MKP宠物社区的AI助手。\n\n" +
               "可用工具列表：\n{tools_description}\n\n" +
               "根据用户问题判断是否需要调用工具；需要时直接调用，拿到结果后再给出最终回答。";
    }

    /**
     * 获取可用工具列表（管理后台用）
     */
    @Override
    public List<String> getAvailableTools() {
        return toolRegistry.getAvailableToolNames();
    }

    /**
     * 意图规划器需要的历史上下文：从 ChatMemory 取窗口内的消息，转成 role/content 列表。
     */
    private List<Map<String, String>> historyForPlanner(Long conversationId) {
        List<Message> window = chatMemory.get(String.valueOf(conversationId));
        List<Map<String, String>> history = new ArrayList<>(window.size());
        for (Message message : window) {
            String content = message.getText();
            if (content == null || content.isBlank()) {
                continue;
            }
            history.add(Map.of("role", message.getMessageType().getValue(), "content", content));
        }
        return history;
    }

    /**
     * SSE 发送封装：客户端断开时只记录日志，不打断主流程。
     */
    private void sendEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (Exception e) {
            log.debug("SSE 事件发送失败: {}", eventName);
        }
    }

    // ==================== 会话管理 ====================

    @Override
    public R<List<ChatbotConversation>> listConversations() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail("User not authenticated");
        }
        List<ChatbotConversation> list = conversationMapper.selectList(
                new LambdaQueryWrapper<ChatbotConversation>()
                        .eq(ChatbotConversation::getUserId, userId)
                        .orderByDesc(ChatbotConversation::getUpdateTime));
        return R.ok(list);
    }

    @Override
    public R<List<ChatbotMessage>> getConversationMessages(Long conversationId) {
        if (conversationId == null) {
            return R.fail("conversationId cannot be null");
        }
        List<ChatbotMessage> list = messageMapper.selectList(
                new LambdaQueryWrapper<ChatbotMessage>()
                        .eq(ChatbotMessage::getConversationId, conversationId)
                        .orderByAsc(ChatbotMessage::getCreateTime));
        return R.ok(list);
    }

    @Override
    public R<Void> deleteConversation(Long conversationId) {
        if (conversationId == null) {
            return R.fail("conversationId cannot be null");
        }
        Long userId = UserContext.getUserId();
        messageMapper.delete(new LambdaQueryWrapper<ChatbotMessage>()
                .eq(ChatbotMessage::getConversationId, conversationId));
        conversationMapper.delete(new LambdaQueryWrapper<ChatbotConversation>()
                .eq(ChatbotConversation::getId, conversationId)
                .eq(ChatbotConversation::getUserId, userId));
        chatMemory.clear(String.valueOf(conversationId));
        return R.ok();
    }

    // ==================== MySQL 持久化 ====================

    private void saveToMySQL(Long conversationId, String question, String reply,
                             List<ChatbotPostVO> posts, List<PostingVO> postings) {
        ChatbotMessage userMsg = new ChatbotMessage();
        userMsg.setConversationId(conversationId);
        userMsg.setRole("user");
        userMsg.setContent(question);
        messageMapper.insert(userMsg);

        ChatbotMessage assistantMsg = new ChatbotMessage();
        assistantMsg.setConversationId(conversationId);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(reply);
        if (posts != null && !posts.isEmpty()) {
            assistantMsg.setPostsJson(JSONUtil.toJsonStr(posts));
        }
        if (postings != null && !postings.isEmpty()) {
            assistantMsg.setPostingsJson(JSONUtil.toJsonStr(postings));
        }
        messageMapper.insert(assistantMsg);

        conversationMapper.update(null,
                new LambdaUpdateWrapper<ChatbotConversation>()
                        .eq(ChatbotConversation::getId, conversationId)
                        .set(ChatbotConversation::getUpdateTime, java.time.LocalDateTime.now()));
    }

    // ==================== 知识库管理（不变） ====================

    @Override
    public R<List<ChatbotKnowledge>> listKnowledge() {
        List<ChatbotKnowledge> list = knowledgeMapper.selectList(
                new LambdaQueryWrapper<ChatbotKnowledge>()
                        .eq(ChatbotKnowledge::getDeleted, 0));
        return R.ok(list);
    }

    @Override
    public R<Void> addKnowledge(KnowledgeSaveDTO dto) {
        if (dto.getKeyword() == null || dto.getKeyword().isBlank()) {
            return R.fail("keyword cannot be empty");
        }
        if (dto.getAnswer() == null || dto.getAnswer().isBlank()) {
            return R.fail("answer cannot be empty");
        }
        ChatbotKnowledge knowledge = new ChatbotKnowledge();
        knowledge.setKeyword(dto.getKeyword());
        knowledge.setAnswer(dto.getAnswer());
        knowledge.setCategory(dto.getCategory());
        knowledge.setDeleted(0);
        knowledgeMapper.insert(knowledge);
        // 新增后同步向量索引
        syncKnowledgeVector(knowledge.getId());
        return R.ok();
    }

    @Override
    public R<Void> updateKnowledge(Long id, KnowledgeSaveDTO dto) {
        ChatbotKnowledge knowledge = knowledgeMapper.selectById(id);
        if (knowledge == null) {
            return R.fail(404, "Knowledge entry not found");
        }
        if (dto.getKeyword() != null && !dto.getKeyword().isBlank()) {
            knowledge.setKeyword(dto.getKeyword());
        }
        if (dto.getAnswer() != null && !dto.getAnswer().isBlank()) {
            knowledge.setAnswer(dto.getAnswer());
        }
        if (dto.getCategory() != null) {
            knowledge.setCategory(dto.getCategory());
        }
        knowledgeMapper.updateById(knowledge);
        // 更新后同步向量索引
        syncKnowledgeVector(id);
        return R.ok();
    }

    @Override
    public R<Void> deleteKnowledge(Long id) {
        ChatbotKnowledge knowledge = knowledgeMapper.selectById(id);
        if (knowledge == null) {
            return R.fail(404, "Knowledge entry not found");
        }
        knowledgeMapper.deleteById(id);
        // 删除后清理向量索引
        removeKnowledgeVector(id);
        return R.ok();
    }

    @Override
    public R<Void> batchDeleteKnowledge(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return R.fail("请选择要删除的知识");
        }
        knowledgeMapper.deleteBatchIds(ids);
        // 批量删除后清理向量索引
        if (knowledgeVectorSyncService != null) {
            try {
                knowledgeVectorSyncService.removeMany(ids);
            } catch (Exception e) {
                log.error("批量清理知识向量失败", e);
            }
        }
        return R.ok();
    }

    /**
     * 同步单条知识向量（向量服务不可用时静默跳过，不影响知识库 CRUD）
     */
    private void syncKnowledgeVector(Long id) {
        if (knowledgeVectorSyncService == null || id == null) return;
        try {
            knowledgeVectorSyncService.syncOne(id);
        } catch (Exception e) {
            log.error("知识向量同步失败: id={}", id, e);
        }
    }

    /**
     * 清理单条知识向量（向量服务不可用时静默跳过）
     */
    private void removeKnowledgeVector(Long id) {
        if (knowledgeVectorSyncService == null || id == null) return;
        try {
            knowledgeVectorSyncService.removeOne(id);
        } catch (Exception e) {
            log.error("知识向量清理失败: id={}", id, e);
        }
    }
}

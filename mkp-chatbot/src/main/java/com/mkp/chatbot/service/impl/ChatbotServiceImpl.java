package com.mkp.chatbot.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mkp.chatbot.mapper.ChatbotConversationMapper;
import com.mkp.chatbot.mapper.ChatbotMessageMapper;
import com.mkp.chatbot.mapper.ChatbotKnowledgeMapper;
import com.mkp.chatbot.service.ChatbotService;
import com.mkp.chatbot.service.prompt.PromptTemplateService;
import com.mkp.chatbot.service.react.*;
import com.mkp.chatbot.service.vector.KnowledgeVectorSyncService;
import com.mkp.common.dto.ChatbotReplyVO;
import com.mkp.common.dto.KnowledgeSaveDTO;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ChatbotServiceImpl implements ChatbotService {

    private static final String SESSION_KEY_PREFIX = "chatbot:session:";
    private static final long SESSION_TTL_MINUTES = 30;
    private static final int MAX_HISTORY_ROUNDS = 10;

    private final ChatbotKnowledgeMapper knowledgeMapper;
    private final ChatbotConversationMapper conversationMapper;
    private final ChatbotMessageMapper messageMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ReActEngine reactEngine;

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

    // ==================== 核心问答（ReAct 版本） ====================

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

        // 第二步：从 Redis 读取历史消息（Redis 优先，MySQL 兜底）
        List<Map<String, String>> history = loadHistory(userId, conversationId);

        // 第三步：构建 ReAct 消息列表
        List<Message> messages = new ArrayList<>();

        // System Prompt（动态注入工具描述）
        messages.add(new Message("system", buildSystemPrompt()));

        // 历史对话
        for (Map<String, String> h : history) {
            messages.add(new Message(h.get("role"), h.get("content")));
        }

        // 当前问题
        messages.add(new Message("user", question));

        // 第四步：执行 ReAct 推理循环
        ReActContext context = new ReActContext();
        String answer;
        try {
            answer = reactEngine.execute(messages, question, context);
        } catch (Exception e) {
            log.error("ReAct 推理失败，降级处理", e);
            answer = "抱歉，我暂时无法回答您的问题，请稍后再试。";
        }

        if (answer == null || answer.isBlank()) {
            answer = "抱歉，我暂时无法回答您的问题，请稍后再试。";
        }

        // 第五步：将本轮问答写入历史
        history.add(Map.of("role", "user", "content", question));
        history.add(Map.of("role", "assistant", "content", answer));
        trimAndSaveHistory(userId, conversationId, history);

        // 第六步：写入 MySQL（持久化）
        saveToMySQL(conversationId, question, answer);

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

            // 第二步：从 Redis 读取历史消息（复用）
            List<Map<String, String>> history = loadHistory(userId, conversationId);

            // 第三步：构建 ReAct 消息列表（复用）
            List<Message> messages = new ArrayList<>();
            messages.add(new Message("system", buildSystemPrompt()));
            for (Map<String, String> h : history) {
                messages.add(new Message(h.get("role"), h.get("content")));
            }
            messages.add(new Message("user", question));

            // 第四步：执行 ReAct 流式推理
            ReActContext context = new ReActContext();
            String answer = reactEngine.executeStreaming(messages, question, context, emitter);

            // 第五步：将本轮问答写入历史（复用）
            if (answer == null || answer.isBlank()) {
                answer = "抱歉，我暂时无法回答您的问题，请稍后再试。";
            }
            history.add(Map.of("role", "user", "content", question));
            history.add(Map.of("role", "assistant", "content", answer));
            trimAndSaveHistory(userId, conversationId, history);

            // 第六步：写入 MySQL 持久化（复用）
            saveToMySQL(conversationId, question, answer);

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
    private String buildSystemPrompt() {
        // 1. 加载模板内容（带缓存）
        String template = promptTemplateService.getTemplateContent(defaultPromptScene);

        // 兜底：如果数据库无模板，使用内置极简模板（保证服务不挂）
        if (template == null || template.isBlank()) {
            log.error("Prompt 模板加载失败，使用内置兜底模板: scene={}", defaultPromptScene);
            template = buildFallbackTemplate();
        }

        // 2. 动态注入工具描述
        String toolsDesc = buildToolsDescription();

        // 3. 替换占位符
        return template.replace("{tools_description}", toolsDesc);
    }

    /**
     * 构建工具描述字符串（从 ToolRegistry 动态获取）
     */
    private String buildToolsDescription() {
        StringBuilder toolsDesc = new StringBuilder();

        toolRegistry.getAllTools().forEach(tool -> {
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
     * 兜底 Prompt 模板（极简版，保证数据库异常时服务可用）
     */
    private String buildFallbackTemplate() {
        return "你是MKP宠物社区的AI助手。\n\n" +
               "可用工具列表：\n{tools_description}\n\n" +
               "请根据用户问题，思考是否需要调用工具获取信息，然后给出回答。";
    }

    /**
     * 获取可用工具列表（管理后台用）
     */
    @Override
    public List<String> getAvailableTools() {
        return toolRegistry.getAvailableToolNames();
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
        stringRedisTemplate.delete(SESSION_KEY_PREFIX + userId + ":" + conversationId);
        return R.ok();
    }

    // ==================== Redis 历史读写 ====================

    private List<Map<String, String>> loadHistory(Long userId, Long conversationId) {
        String redisKey = SESSION_KEY_PREFIX + userId + ":" + conversationId;
        String historyJson = stringRedisTemplate.opsForValue().get(redisKey);

        if (historyJson != null && !historyJson.isEmpty()) {
            return parseHistoryJson(historyJson);
        }

        // Redis 未命中 -> MySQL 兜底
        List<ChatbotMessage> msgList = messageMapper.selectList(
                new LambdaQueryWrapper<ChatbotMessage>()
                        .eq(ChatbotMessage::getConversationId, conversationId)
                        .orderByAsc(ChatbotMessage::getCreateTime)
                        .last("LIMIT " + MAX_HISTORY_ROUNDS * 2));

        List<Map<String, String>> history = new ArrayList<>();
        for (ChatbotMessage msg : msgList) {
            Map<String, String> map = new HashMap<>();
            map.put("role", msg.getRole());
            map.put("content", msg.getContent());
            history.add(map);
        }

        if (!history.isEmpty()) {
            stringRedisTemplate.opsForValue().set(
                    redisKey, JSONUtil.toJsonStr(history),
                    SESSION_TTL_MINUTES, TimeUnit.MINUTES);
        }

        return history;
    }

    private void trimAndSaveHistory(Long userId, Long conversationId, List<Map<String, String>> history) {
        while (history.size() > MAX_HISTORY_ROUNDS * 2) {
            history.remove(0);
        }

        String redisKey = SESSION_KEY_PREFIX + userId + ":" + conversationId;
        stringRedisTemplate.opsForValue().set(
                redisKey, JSONUtil.toJsonStr(history),
                SESSION_TTL_MINUTES, TimeUnit.MINUTES);
    }

    private List<Map<String, String>> parseHistoryJson(String json) {
        JSONArray array = JSONUtil.parseArray(json);
        List<Map<String, String>> history = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JSONObject obj = array.getJSONObject(i);
            Map<String, String> map = new HashMap<>();
            map.put("role", obj.getStr("role"));
            map.put("content", obj.getStr("content"));
            history.add(map);
        }
        return history;
    }

    // ==================== MySQL 持久化 ====================

    private void saveToMySQL(Long conversationId, String question, String reply) {
        ChatbotMessage userMsg = new ChatbotMessage();
        userMsg.setConversationId(conversationId);
        userMsg.setRole("user");
        userMsg.setContent(question);
        messageMapper.insert(userMsg);

        ChatbotMessage assistantMsg = new ChatbotMessage();
        assistantMsg.setConversationId(conversationId);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(reply);
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

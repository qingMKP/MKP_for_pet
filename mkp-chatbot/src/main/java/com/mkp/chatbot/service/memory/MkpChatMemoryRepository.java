package com.mkp.chatbot.service.memory;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mkp.chatbot.mapper.ChatbotMessageMapper;
import com.mkp.common.entity.ChatbotMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * ChatMemory 的存储适配器：Redis 做热存储，MySQL 的 mkp_chatbot_message 做持久化兜底。
 *
 * 只负责「按 conversationId 读写消息列表」，窗口裁剪由 MessageWindowChatMemory 完成。
 */
@Slf4j
@Repository
public class MkpChatMemoryRepository implements ChatMemoryRepository {

    private static final String KEY_PREFIX = "chatbot:mem:";
    private static final long TTL_MINUTES = 30;
    private static final int FALLBACK_LIMIT = 20;

    private final StringRedisTemplate stringRedisTemplate;
    private final ChatbotMessageMapper messageMapper;

    public MkpChatMemoryRepository(StringRedisTemplate stringRedisTemplate,
                                   ChatbotMessageMapper messageMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.messageMapper = messageMapper;
    }

    @Override
    public List<String> findConversationIds() {
        List<ChatbotMessage> rows = messageMapper.selectList(
                new QueryWrapper<ChatbotMessage>().select("DISTINCT conversation_id"));
        List<String> ids = new ArrayList<>(rows.size());
        for (ChatbotMessage row : rows) {
            if (row.getConversationId() != null) {
                ids.add(String.valueOf(row.getConversationId()));
            }
        }
        return ids;
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        String key = KEY_PREFIX + conversationId;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null && !json.isEmpty()) {
            return parse(json);
        }

        List<Message> fromDb = loadFromDb(conversationId);
        if (!fromDb.isEmpty()) {
            saveAll(conversationId, fromDb);
        }
        return fromDb;
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        JSONArray array = new JSONArray();
        for (Message message : messages) {
            if (message == null) {
                continue;
            }
            JSONObject row = new JSONObject();
            row.set("role", message.getMessageType().getValue());
            row.set("content", message.getText() == null ? "" : message.getText());
            array.add(row);
        }
        stringRedisTemplate.opsForValue().set(
                KEY_PREFIX + conversationId, array.toString(), TTL_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        stringRedisTemplate.delete(KEY_PREFIX + conversationId);
    }

    /**
     * Redis 未命中时回 MySQL 捞最近 20 条，按时间正序返回。
     */
    private List<Message> loadFromDb(String conversationId) {
        long id;
        try {
            id = Long.parseLong(conversationId);
        } catch (NumberFormatException e) {
            log.warn("会话 id 非数字，跳过 MySQL 兜底: {}", conversationId);
            return Collections.emptyList();
        }
        List<ChatbotMessage> rows = messageMapper.selectList(
                new LambdaQueryWrapper<ChatbotMessage>()
                        .eq(ChatbotMessage::getConversationId, id)
                        .orderByDesc(ChatbotMessage::getCreateTime)
                        .last("LIMIT " + FALLBACK_LIMIT));
        Collections.reverse(rows);

        List<Message> messages = new ArrayList<>(rows.size());
        for (ChatbotMessage row : rows) {
            String content = row.getContent();
            if (content == null || content.isEmpty()) {
                continue;
            }
            if ("assistant".equalsIgnoreCase(row.getRole())) {
                messages.add(new AssistantMessage(content));
            } else if ("system".equalsIgnoreCase(row.getRole())) {
                messages.add(new SystemMessage(content));
            } else {
                messages.add(new UserMessage(content));
            }
        }
        return messages;
    }

    private List<Message> parse(String json) {
        JSONArray array = JSONUtil.parseArray(json);
        List<Message> messages = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JSONObject row = array.getJSONObject(i);
            String content = row.getStr("content");
            if (content == null || content.isEmpty()) {
                continue;
            }
            String role = row.getStr("role");
            if (MessageType.ASSISTANT.getValue().equalsIgnoreCase(role)) {
                messages.add(new AssistantMessage(content));
            } else if (MessageType.SYSTEM.getValue().equalsIgnoreCase(role)) {
                messages.add(new SystemMessage(content));
            } else {
                messages.add(new UserMessage(content));
            }
        }
        return messages;
    }
}

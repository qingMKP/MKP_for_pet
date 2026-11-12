package com.mkp.chatbot.service.vector;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mkp.chatbot.mapper.ChatbotKnowledgeMapper;
import com.mkp.common.entity.ChatbotKnowledge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;

import jakarta.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 知识库向量同步服务
 *
 * 负责在知识库变更后将文本转为 Embedding 向量并写入 Redis Stack。
 * 支持全量同步（启动时 / 手动触发）和单条增量同步（增删改时自动触发）。
 */
@Slf4j
@Service
public class KnowledgeVectorSyncService {

    private final ChatbotKnowledgeMapper knowledgeMapper;
    private final EmbeddingService embeddingService;
    private final JedisPooled jedisPooled;
    private final StringRedisTemplate stringRedisTemplate;
    private final VectorIndexManager indexManager;

    private static final String SYNC_LOCK_KEY = "knowledge:vector:sync:lock";

    @Value("${mkp.knowledge.vector.enabled:true}")
    private boolean enabled;

    public KnowledgeVectorSyncService(ChatbotKnowledgeMapper knowledgeMapper,
                                      EmbeddingService embeddingService,
                                      JedisPooled jedisPooled,
                                      StringRedisTemplate stringRedisTemplate,
                                      VectorIndexManager indexManager) {
        this.knowledgeMapper = knowledgeMapper;
        this.embeddingService = embeddingService;
        this.jedisPooled = jedisPooled;
        this.stringRedisTemplate = stringRedisTemplate;
        this.indexManager = indexManager;
    }

    /**
     * 应用启动后异步触发全量同步（不阻塞主线程）
     */
    @PostConstruct
    public void initOnStartup() {
        if (!enabled) return;

        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(3000); // 等待其余 Bean / 数据源就绪
                syncAll();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "vector-sync-startup");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 全量同步所有知识库数据到 Redis 向量索引
     * 调用时机：应用启动后首次加载 / 管理后台手动触发
     */
    public void syncAll() {
        if (!enabled || !indexManager.ensureIndex()) {
            log.warn("向量索引不可用或检索未启用，跳过全量同步");
            return;
        }

        Boolean lock = stringRedisTemplate.opsForValue()
                .setIfAbsent(SYNC_LOCK_KEY, "1", 10, TimeUnit.MINUTES);
        if (Boolean.FALSE.equals(lock)) {
            log.info("向量同步正在进行中，跳过本次请求");
            return;
        }

        try {
            List<ChatbotKnowledge> list = knowledgeMapper.selectList(
                    new LambdaQueryWrapper<ChatbotKnowledge>()
                            .eq(ChatbotKnowledge::getDeleted, 0));

            if (list.isEmpty()) {
                log.info("知识库为空，跳过向量同步");
                return;
            }

            log.info("开始全量向量同步，共 {} 条知识", list.size());

            // 批量 Embedding（减少 API 调用次数）
            List<String> texts = new ArrayList<>();
            for (ChatbotKnowledge k : list) {
                texts.add(k.getKeyword() + " " + k.getAnswer());
            }
            List<List<Float>> vectors = embeddingService.embedBatch(texts);

            // 批量写入 Redis（JedisPooled 自动管理连接）
            for (int i = 0; i < list.size(); i++) {
                writeVector(list.get(i), vectors.get(i));
            }

            log.info("向量同步完成，已写入 {} 条向量", list.size());

            // 删除旧的 JSON 全量缓存，避免与新向量数据不一致
            stringRedisTemplate.delete("chatbot:knowledge:all");

        } catch (Exception e) {
            log.error("向量同步失败", e);
        } finally {
            stringRedisTemplate.delete(SYNC_LOCK_KEY);
        }
    }

    /**
     * 单条同步（新增 / 修改知识时调用）
     */
    public void syncOne(Long knowledgeId) {
        if (!enabled || !indexManager.ensureIndex()) return;

        try {
            ChatbotKnowledge k = knowledgeMapper.selectById(knowledgeId);
            if (k == null) {
                log.debug("知识不存在或已删除，跳过同步: id={}", knowledgeId);
                return;
            }

            String text = k.getKeyword() + " " + k.getAnswer();
            List<Float> vector = embeddingService.embed(text);
            writeVector(k, vector);

            log.info("单条向量同步完成: id={}", knowledgeId);
        } catch (Exception e) {
            log.error("单条向量同步失败: id={}", knowledgeId, e);
        }
    }

    /**
     * 删除单条知识的向量（逻辑删除知识后调用）
     */
    public void removeOne(Long knowledgeId) {
        if (!enabled || knowledgeId == null) return;
        jedisPooled.del(VectorIndexManager.VECTOR_KEY_PREFIX + knowledgeId);
        log.info("已删除知识向量: id={}", knowledgeId);
    }

    /**
     * 批量删除知识向量（批量逻辑删除后调用）
     */
    public void removeMany(List<Long> ids) {
        if (!enabled || ids == null || ids.isEmpty()) return;
        for (Long id : ids) {
            jedisPooled.del(VectorIndexManager.VECTOR_KEY_PREFIX + id);
        }
        log.info("已批量删除知识向量: {} 条", ids.size());
    }

    /**
     * 将单条知识的向量字段写入 Redis Hash（binary 方式，避免编码问题）
     */
    private void writeVector(ChatbotKnowledge k, List<Float> vector) {
        byte[] rawKey = (VectorIndexManager.VECTOR_KEY_PREFIX + k.getId()).getBytes(StandardCharsets.UTF_8);

        Map<byte[], byte[]> fields = new HashMap<>();
        fields.put("keyword".getBytes(StandardCharsets.UTF_8), safeBytes(k.getKeyword()));
        fields.put("answer".getBytes(StandardCharsets.UTF_8), safeBytes(k.getAnswer()));
        fields.put("category".getBytes(StandardCharsets.UTF_8), safeBytes(k.getCategory()));
        fields.put("embedding".getBytes(StandardCharsets.UTF_8), floatsToBytes(vector));

        jedisPooled.hset(rawKey, fields);
    }

    private byte[] safeBytes(String s) {
        return (s != null ? s : "").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * float 列表 → 小端序 4 字节 float 数组
     */
    private byte[] floatsToBytes(List<Float> floats) {
        ByteBuffer buf = ByteBuffer.allocate(floats.size() * 4);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        for (float f : floats) {
            buf.putFloat(f);
        }
        return buf.array();
    }
}

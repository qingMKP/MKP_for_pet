package com.mkp.chatbot.service.vector;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mkp.chatbot.mapper.ChatbotKnowledgeMapper;
import com.mkp.common.entity.ChatbotKnowledge;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 知识库向量同步服务。
 *
 * 负责在知识库变更后把文本转成 Embedding 并写入 Milvus。
 * 支持全量同步（启动时 / 手动触发）和单条增量同步（增删改时自动触发）。
 * Redis 只承担同步互斥锁和关键词兜底缓存，不再存放向量。
 */
@Slf4j
@Service
public class KnowledgeVectorSyncService {

    private static final String SYNC_LOCK_KEY = "knowledge:vector:sync:lock";
    private static final String KNOWLEDGE_CACHE_KEY = "chatbot:knowledge:all";

    private final ChatbotKnowledgeMapper knowledgeMapper;
    private final EmbeddingService embeddingService;
    private final KnowledgeVectorStore knowledgeVectorStore;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${mkp.knowledge.vector.enabled:true}")
    private boolean enabled;

    public KnowledgeVectorSyncService(ChatbotKnowledgeMapper knowledgeMapper,
                                      EmbeddingService embeddingService,
                                      KnowledgeVectorStore knowledgeVectorStore,
                                      StringRedisTemplate stringRedisTemplate) {
        this.knowledgeMapper = knowledgeMapper;
        this.embeddingService = embeddingService;
        this.knowledgeVectorStore = knowledgeVectorStore;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 应用启动后异步触发全量同步（不阻塞主线程）
     */
    @PostConstruct
    public void initOnStartup() {
        if (!enabled) {
            return;
        }

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
     * 全量同步所有知识到 Milvus。
     * 调用时机：应用启动后首次加载 / 管理后台手动触发
     */
    public void syncAll() {
        if (!enabled || !knowledgeVectorStore.isAvailable()) {
            log.warn("向量库不可用或检索未启用，跳过全量同步");
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

            List<String> texts = new ArrayList<>(list.size());
            for (ChatbotKnowledge k : list) {
                texts.add(embedText(k));
            }
            List<List<Float>> vectors = embeddingService.embedBatch(texts);

            List<KnowledgeVectorStore.KnowledgeVector> rows = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                rows.add(new KnowledgeVectorStore.KnowledgeVector(list.get(i), vectors.get(i)));
            }
            knowledgeVectorStore.writeBatch(rows);

            log.info("向量同步完成，已写入 {} 条向量", rows.size());

            // 删除旧的 JSON 全量缓存，避免与新向量数据不一致
            stringRedisTemplate.delete(KNOWLEDGE_CACHE_KEY);

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
        if (!enabled || !knowledgeVectorStore.isAvailable()) {
            return;
        }

        try {
            ChatbotKnowledge k = knowledgeMapper.selectById(knowledgeId);
            if (k == null) {
                log.debug("知识不存在或已删除，跳过同步: id={}", knowledgeId);
                return;
            }

            List<Float> vector = embeddingService.embed(embedText(k));
            knowledgeVectorStore.writeBatch(
                    List.of(new KnowledgeVectorStore.KnowledgeVector(k, vector)));

            log.info("单条向量同步完成: id={}", knowledgeId);
        } catch (Exception e) {
            log.error("单条向量同步失败: id={}", knowledgeId, e);
        }
    }

    /**
     * 删除单条知识的向量（逻辑删除知识后调用）
     */
    public void removeOne(Long knowledgeId) {
        if (!enabled || knowledgeId == null) {
            return;
        }
        knowledgeVectorStore.removeAll(List.of(knowledgeId));
        log.info("已删除知识向量: id={}", knowledgeId);
    }

    /**
     * 批量删除知识向量（批量逻辑删除后调用）
     */
    public void removeMany(List<Long> ids) {
        if (!enabled || ids == null || ids.isEmpty()) {
            return;
        }
        knowledgeVectorStore.removeAll(ids);
        log.info("已批量删除知识向量: {} 条", ids.size());
    }

    private String embedText(ChatbotKnowledge k) {
        String keyword = k.getKeyword() == null ? "" : k.getKeyword();
        String answer = k.getAnswer() == null ? "" : k.getAnswer();
        return keyword + " " + answer;
    }
}

package com.mkp.chatbot.service.vector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.IndexDataType;
import redis.clients.jedis.search.schemafields.TagField;
import redis.clients.jedis.search.schemafields.TextField;
import redis.clients.jedis.search.schemafields.VectorField;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis Stack 向量索引管理器
 *
 * 职责：
 * 1. 应用启动时创建 RediSearch HNSW 向量索引（已存在则跳过）
 * 2. 检测 RediSearch 模块是否可用（普通 Redis 无此模块时自动降级）
 * 3. 索引创建结果带缓存：成功/模块缺失只检测一次，连接异常等瞬时错误下次重试
 */
@Slf4j
@Component
public class VectorIndexManager {

    /** RediSearch 索引名 */
    public static final String VECTOR_INDEX = "idx:knowledge:vector";

    /** Redis Hash Key 前缀 */
    public static final String VECTOR_KEY_PREFIX = "knowledge:vector:";

    private final JedisPooled jedisPooled;

    @Value("${mkp.knowledge.vector.embedding-dim:1024}")
    private int embeddingDim;

    @Value("${mkp.knowledge.vector.enabled:true}")
    private boolean enabled;

    /**
     * 索引状态缓存：null=未检测，true=可用，false=确定不可用（如普通 Redis 无 RediSearch 模块）
     */
    private volatile Boolean indexReady = null;

    public VectorIndexManager(JedisPooled jedisPooled) {
        this.jedisPooled = jedisPooled;
    }

    @PostConstruct
    public void initOnStartup() {
        if (!enabled) {
            log.info("向量检索未启用（mkp.knowledge.vector.enabled=false），跳过索引初始化");
            return;
        }
        ensureIndex();
    }

    /**
     * 确保 RediSearch 向量索引已创建
     *
     * 索引字段：
     *   - keyword:   TEXT（知识关键词）
     *   - answer:    TEXT（知识答案正文）
     *   - category:  TAG（分类标签，支持精确过滤）
     *   - embedding: VECTOR（FLOAT32，HNSW 索引，余弦距离）
     *
     * @return 向量索引是否可用
     */
    public boolean ensureIndex() {
        Boolean cached = indexReady;
        if (cached != null) {
            return cached;
        }

        synchronized (this) {
            if (indexReady != null) {
                return indexReady;
            }

            try {
                Map<String, Object> vectorAttrs = new HashMap<>();
                vectorAttrs.put("TYPE", "FLOAT32");
                vectorAttrs.put("DIM", embeddingDim);
                vectorAttrs.put("DISTANCE_METRIC", "COSINE");

                jedisPooled.ftCreate(VECTOR_INDEX,
                        FTCreateParams.createParams()
                                .on(IndexDataType.HASH)
                                .addPrefix(VECTOR_KEY_PREFIX),
                        new TextField("keyword"),
                        new TextField("answer"),
                        new TagField("category"),
                        new VectorField("embedding",
                                VectorField.VectorAlgorithm.HNSW, vectorAttrs));

                indexReady = true;
                log.info("Redis 向量索引创建成功: {}（维度 {}）", VECTOR_INDEX, embeddingDim);

            } catch (JedisConnectionException e) {
                // 瞬时错误（Redis 未启动等），不缓存，下次重试
                log.warn("Redis 连接失败，向量索引初始化跳过，稍后将自动重试: {}", e.getMessage());
                return false;
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("Index already exists")) {
                    indexReady = true;
                    log.info("Redis 向量索引已存在，跳过创建: {}", VECTOR_INDEX);
                } else if (isModuleMissing(msg)) {
                    indexReady = false;
                    log.warn("当前 Redis 不支持 RediSearch 模块（需要 Redis Stack 版本）: {}", msg);
                    log.warn("知识库检索将自动降级为关键词匹配模式。" +
                            "升级方法：将 Redis 镜像换为 redis/redis-stack-server:7.2.0");
                } else {
                    // 其他未知错误，不缓存，下次重试
                    log.warn("向量索引初始化失败，稍后将自动重试: {}", msg);
                    return false;
                }
            }
            return indexReady;
        }
    }

    /**
     * 判断异常是否代表 RediSearch 模块缺失（普通 Redis 会返回 unknown command）
     */
    private boolean isModuleMissing(String msg) {
        String lower = msg.toLowerCase();
        return lower.contains("unknown command") || lower.contains("no such module")
                || lower.contains("unsupported command");
    }
}

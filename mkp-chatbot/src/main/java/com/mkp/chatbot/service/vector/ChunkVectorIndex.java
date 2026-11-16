package com.mkp.chatbot.service.vector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.IndexDataType;
import redis.clients.jedis.search.schemafields.NumericField;
import redis.clients.jedis.search.schemafields.TagField;
import redis.clients.jedis.search.schemafields.TextField;
import redis.clients.jedis.search.schemafields.VectorField;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * 文档子块的 Redis 向量索引。
 *
 * 和短问答的 idx:knowledge:vector 分开建，互不影响：
 * 短问答索引存 keyword/answer，这边存的是子块正文 + 父子块 ID。
 */
@Slf4j
@Component
public class ChunkVectorIndex {

    /** RediSearch 索引名 */
    public static final String CHUNK_INDEX = "idx:knowledge:chunk";

    /** Redis Hash Key 前缀：knowledge:chunk:{chunkId} */
    public static final String CHUNK_KEY_PREFIX = "knowledge:chunk:";

    private final JedisPooled jedisPooled;

    @Value("${mkp.knowledge.vector.embedding-dim:1024}")
    private int embeddingDim;

    @Value("${mkp.knowledge.vector.enabled:true}")
    private boolean enabled;

    private volatile Boolean indexReady = null;

    public ChunkVectorIndex(JedisPooled jedisPooled) {
        this.jedisPooled = jedisPooled;
    }

    @PostConstruct
    public void initOnStartup() {
        if (!enabled) {
            return;
        }
        ensureIndex();
    }

    /**
     * 索引字段：
     *   - content:       TEXT  子块正文
     *   - sectionTitle:  TEXT  所属小节标题
     *   - title:         TEXT  文档标题
     *   - category:      TAG   分类
     *   - documentId:    NUMERIC 文档ID
     *   - parentId:      NUMERIC 父块ID（命中后回查父块正文）
     *   - seq:           NUMERIC 子块序号
     *   - embedding:     VECTOR FLOAT32 HNSW COSINE
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

                jedisPooled.ftCreate(CHUNK_INDEX,
                        FTCreateParams.createParams()
                                .on(IndexDataType.HASH)
                                .addPrefix(CHUNK_KEY_PREFIX),
                        new TextField("content"),
                        new TextField("sectionTitle"),
                        new TextField("title"),
                        new TagField("category"),
                        new NumericField("documentId"),
                        new NumericField("parentId"),
                        new NumericField("seq"),
                        new VectorField("embedding",
                                VectorField.VectorAlgorithm.HNSW, vectorAttrs));

                indexReady = true;
                log.info("文档子块向量索引创建成功: {}（维度 {}）", CHUNK_INDEX, embeddingDim);
            } catch (JedisConnectionException e) {
                log.warn("Redis 连接失败，子块索引初始化跳过: {}", e.getMessage());
                return false;
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("Index already exists")) {
                    indexReady = true;
                    log.info("文档子块向量索引已存在，跳过创建: {}", CHUNK_INDEX);
                } else if (msg.toLowerCase().contains("unknown command")) {
                    indexReady = false;
                    log.warn("当前 Redis 不支持 RediSearch，长文档检索不可用: {}", msg);
                } else {
                    log.warn("文档子块向量索引初始化失败，稍后自动重试: {}", msg);
                    return false;
                }
            }
            return indexReady;
        }
    }
}

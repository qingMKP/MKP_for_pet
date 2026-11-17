package com.mkp.chatbot.service.vector;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.GetLoadStateReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * FAQ 知识向量的 Milvus 实现。
 *
 * collection 里一条知识一行，主键就是 MySQL 的 knowledge id，召回后无需再回表。
 * 相似度口径：Milvus COSINE 返回相似度（越大越像），直接用 score 比阈值。
 */
@Slf4j
@Service
public class MilvusKnowledgeVectorStore implements KnowledgeVectorStore {

    private final MilvusClientV2 milvusClient;
    private final EmbeddingService embeddingService;
    private final Gson gson = new Gson();

    @Value("${mkp.knowledge.vector.milvus.faq-collection:mkp_knowledge_faq}")
    private String collection;

    @Value("${mkp.knowledge.vector.embedding-dim:1024}")
    private int dimension;

    @Value("${mkp.knowledge.vector.milvus.m:16}")
    private int hnswM;

    @Value("${mkp.knowledge.vector.milvus.ef-construction:200}")
    private int efConstruction;

    @Value("${mkp.knowledge.vector.milvus.search-ef:64}")
    private int searchEf;

    @Value("${mkp.knowledge.vector.enabled:true}")
    private boolean enabled;

    private volatile boolean collectionReady = false;

    public MilvusKnowledgeVectorStore(MilvusClientV2 milvusClient, EmbeddingService embeddingService) {
        this.milvusClient = milvusClient;
        this.embeddingService = embeddingService;
    }

    @PostConstruct
    public void initOnStartup() {
        if (!enabled) {
            return;
        }
        try {
            ensureCollection();
        } catch (Exception e) {
            log.warn("Milvus FAQ collection 启动探针失败，FAQ 向量检索暂不可用: {}", e.toString());
        }
    }

    @Override
    public boolean isAvailable() {
        return enabled && ensureCollection();
    }

    private boolean ensureCollection() {
        if (collectionReady) {
            return true;
        }
        synchronized (this) {
            if (collectionReady) {
                return true;
            }
            try {
                Boolean exists = milvusClient.hasCollection(
                        HasCollectionReq.builder().collectionName(collection).build());
                if (!Boolean.TRUE.equals(exists)) {
                    createCollection();
                } else {
                    Boolean loaded = milvusClient.getLoadState(
                            GetLoadStateReq.builder().collectionName(collection).build());
                    if (!Boolean.TRUE.equals(loaded)) {
                        milvusClient.loadCollection(
                                LoadCollectionReq.builder().collectionName(collection).build());
                        log.info("Milvus FAQ collection 已重新加载: {}", collection);
                    }
                }
                collectionReady = true;
            } catch (Exception e) {
                log.warn("Milvus FAQ collection 初始化失败: {}", e.toString());
                return false;
            }
            return collectionReady;
        }
    }

    private void createCollection() {
        CreateCollectionReq.CollectionSchema schema = milvusClient.createSchema();
        schema.addField(AddFieldReq.builder()
                .fieldName("knowledge_id").dataType(DataType.VarChar).maxLength(64)
                .isPrimaryKey(true).autoID(false).build());
        schema.addField(AddFieldReq.builder().fieldName("keyword").dataType(DataType.VarChar).maxLength(1024).build());
        schema.addField(AddFieldReq.builder().fieldName("answer").dataType(DataType.VarChar).maxLength(32768).build());
        schema.addField(AddFieldReq.builder().fieldName("category").dataType(DataType.VarChar).maxLength(128).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("embedding").dataType(DataType.FloatVector).dimension(dimension).build());

        milvusClient.createCollection(CreateCollectionReq.builder()
                .collectionName(collection)
                .description("MKP 宠物知识 FAQ 向量")
                .collectionSchema(schema)
                .build());

        IndexParam embeddingIndex = IndexParam.builder()
                .fieldName("embedding")
                .indexName("idx_faq_embedding")
                .indexType(IndexParam.IndexType.HNSW)
                .metricType(IndexParam.MetricType.COSINE)
                .extraParams(Map.of("M", hnswM, "efConstruction", efConstruction))
                .build();
        milvusClient.createIndex(CreateIndexReq.builder()
                .collectionName(collection)
                .indexParams(List.of(embeddingIndex))
                .build());
        milvusClient.loadCollection(LoadCollectionReq.builder().collectionName(collection).build());
        log.info("Milvus FAQ collection 创建完成: {}（维度 {}，HNSW/COSINE，M={} efConstruction={}）",
                collection, dimension, hnswM, efConstruction);
    }

    @Override
    public void writeBatch(List<KnowledgeVector> rows) {
        if (!enabled || rows == null || rows.isEmpty()) {
            return;
        }
        if (!ensureCollection()) {
            return;
        }
        List<JsonObject> data = new ArrayList<>(rows.size());
        for (KnowledgeVector row : rows) {
            JsonObject item = new JsonObject();
            item.addProperty("knowledge_id", String.valueOf(row.knowledge().getId()));
            item.addProperty("keyword", cut(row.knowledge().getKeyword(), 300));
            item.addProperty("answer", cut(row.knowledge().getAnswer(), 8000));
            item.addProperty("category", cut(row.knowledge().getCategory(), 120));
            item.add("embedding", gson.toJsonTree(row.vector()));
            data.add(item);
        }
        try {
            milvusClient.upsert(UpsertReq.builder()
                    .collectionName(collection)
                    .data(data)
                    .build());
            log.info("Milvus FAQ 向量写入完成: {} 条", data.size());
        } catch (Exception e) {
            log.error("Milvus FAQ 向量写入失败: {} 条", data.size(), e);
        }
    }

    @Override
    public void removeAll(List<Long> knowledgeIds) {
        if (!enabled || knowledgeIds == null || knowledgeIds.isEmpty()) {
            return;
        }
        if (!ensureCollection()) {
            return;
        }
        try {
            List<Object> ids = new ArrayList<>(knowledgeIds.size());
            for (Long id : knowledgeIds) {
                ids.add(String.valueOf(id));
            }
            milvusClient.delete(DeleteReq.builder()
                    .collectionName(collection)
                    .ids(ids)
                    .build());
            log.info("Milvus FAQ 向量删除完成: {} 条", ids.size());
        } catch (Exception e) {
            log.error("Milvus FAQ 向量删除失败: {} 条", knowledgeIds.size(), e);
        }
    }

    @Override
    public List<KnowledgeMatch> search(String query, int topK, float threshold) {
        List<KnowledgeMatch> matches = new ArrayList<>();
        if (!enabled || query == null || query.isBlank()) {
            return matches;
        }
        if (!ensureCollection()) {
            return matches;
        }
        try {
            List<Float> queryVector = embeddingService.embed(query);
            SearchResp resp = milvusClient.search(SearchReq.builder()
                    .collectionName(collection)
                    .annsField("embedding")
                    .data(List.of(new FloatVec(queryVector)))
                    .topK(topK)
                    .outputFields(List.of("knowledge_id", "keyword", "answer", "category"))
                    .searchParams(Map.of("ef", Math.max(searchEf, topK)))
                    .build());

            List<List<SearchResp.SearchResult>> groups = resp.getSearchResults();
            if (groups == null || groups.isEmpty()) {
                return matches;
            }
            for (SearchResp.SearchResult result : groups.get(0)) {
                Float score = result.getScore();
                if (score == null || score < threshold) {
                    continue;
                }
                Map<String, Object> entity = result.getEntity();
                if (entity == null) {
                    continue;
                }
                long id = toLong(entity.get("knowledge_id"));
                if (id <= 0) {
                    continue;
                }
                matches.add(new KnowledgeMatch(id,
                        text(entity.get("keyword")),
                        text(entity.get("answer")),
                        text(entity.get("category")),
                        score));
            }
            matches.sort(Comparator.comparingDouble(KnowledgeMatch::getSimilarity).reversed());
        } catch (Exception e) {
            log.error("Milvus FAQ 向量召回失败: query={}", query, e);
        }
        return matches;
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String cut(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }
}

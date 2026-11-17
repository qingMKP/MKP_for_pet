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
 * 子块向量的 Milvus 实现。
 *
 * collection 里只放子块：主键用 MySQL 的 chunk id，命中后按 parent_id 回 MySQL 查父块整节。
 * 相似度口径注意：Milvus COSINE 返回的是相似度（越大越像），不是 RediSearch 那种距离。
 */
@Slf4j
@Service
public class MilvusChunkVectorStore implements ChunkVectorStore {

    private final MilvusClientV2 milvusClient;
    private final EmbeddingService embeddingService;
    private final Gson gson = new Gson();

    @Value("${mkp.knowledge.vector.milvus.collection:mkp_knowledge_chunk}")
    private String collection;

    @Value("${mkp.knowledge.vector.embedding-dim:1024}")
    private int dimension;

    @Value("${mkp.knowledge.vector.chunk.recall-top-k:20}")
    private int recallTopK;

    @Value("${mkp.knowledge.vector.milvus.m:16}")
    private int hnswM;

    @Value("${mkp.knowledge.vector.milvus.ef-construction:200}")
    private int efConstruction;

    @Value("${mkp.knowledge.vector.milvus.search-ef:64}")
    private int searchEf;

    @Value("${mkp.knowledge.vector.enabled:true}")
    private boolean enabled;

    private volatile boolean collectionReady = false;

    public MilvusChunkVectorStore(MilvusClientV2 milvusClient, EmbeddingService embeddingService) {
        this.milvusClient = milvusClient;
        this.embeddingService = embeddingService;
    }

    /**
     * 启动探针：集合不存在就建，存在但没 load 就 load。
     * 连不上只告警，不影响应用启动（RAG 会回落关键词检索）。
     */
    @PostConstruct
    public void initOnStartup() {
        if (!enabled) {
            return;
        }
        try {
            ensureCollection();
        } catch (Exception e) {
            log.warn("Milvus 启动探针失败，长文档向量检索暂不可用: {}", e.toString());
        }
    }

    public boolean ensureCollection() {
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
                        log.info("Milvus collection 已重新加载: {}", collection);
                    }
                }
                collectionReady = true;
            } catch (Exception e) {
                log.warn("Milvus collection 初始化失败: {}", e.toString());
                return false;
            }
            return collectionReady;
        }
    }

    private void createCollection() {
        CreateCollectionReq.CollectionSchema schema = milvusClient.createSchema();
        schema.addField(AddFieldReq.builder()
                .fieldName("chunk_id").dataType(DataType.VarChar).maxLength(64)
                .isPrimaryKey(true).autoID(false).build());
        schema.addField(AddFieldReq.builder().fieldName("document_id").dataType(DataType.Int64).build());
        schema.addField(AddFieldReq.builder().fieldName("parent_id").dataType(DataType.Int64).build());
        schema.addField(AddFieldReq.builder().fieldName("seq").dataType(DataType.Int32).build());
        schema.addField(AddFieldReq.builder().fieldName("category").dataType(DataType.VarChar).maxLength(128).build());
        schema.addField(AddFieldReq.builder().fieldName("title").dataType(DataType.VarChar).maxLength(1024).build());
        schema.addField(AddFieldReq.builder().fieldName("section_title").dataType(DataType.VarChar).maxLength(1024).build());
        schema.addField(AddFieldReq.builder().fieldName("content").dataType(DataType.VarChar).maxLength(4096).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("embedding").dataType(DataType.FloatVector).dimension(dimension).build());

        milvusClient.createCollection(CreateCollectionReq.builder()
                .collectionName(collection)
                .description("MKP RAG 文档子块向量（父子块检索）")
                .collectionSchema(schema)
                .build());

        IndexParam embeddingIndex = IndexParam.builder()
                .fieldName("embedding")
                .indexName("idx_chunk_embedding")
                .indexType(IndexParam.IndexType.HNSW)
                .metricType(IndexParam.MetricType.COSINE)
                .extraParams(Map.of("M", hnswM, "efConstruction", efConstruction))
                .build();
        milvusClient.createIndex(CreateIndexReq.builder()
                .collectionName(collection)
                .indexParams(List.of(embeddingIndex))
                .build());
        milvusClient.loadCollection(LoadCollectionReq.builder().collectionName(collection).build());
        log.info("Milvus collection 创建完成: {}（维度 {}，HNSW/COSINE，M={} efConstruction={}）",
                collection, dimension, hnswM, efConstruction);
    }

    @Override
    public void write(ChunkVector row) {
        writeBatch(List.of(row));
    }

    @Override
    public void writeBatch(List<ChunkVector> rows) {
        if (!enabled || rows == null || rows.isEmpty()) {
            return;
        }
        if (!ensureCollection()) {
            return;
        }
        List<JsonObject> data = new ArrayList<>(rows.size());
        for (ChunkVector row : rows) {
            JsonObject item = new JsonObject();
            item.addProperty("chunk_id", String.valueOf(row.chunk().getId()));
            item.addProperty("document_id", row.chunk().getDocumentId());
            item.addProperty("parent_id", row.chunk().getParentId());
            item.addProperty("seq", row.chunk().getSeq());
            item.addProperty("category", cut(row.category(), 120));
            item.addProperty("title", cut(row.title(), 300));
            item.addProperty("section_title", cut(row.chunk().getSectionTitle(), 300));
            item.addProperty("content", cut(row.chunk().getContent(), 1200));
            item.add("embedding", gson.toJsonTree(row.vector()));
            data.add(item);
        }
        try {
            milvusClient.upsert(UpsertReq.builder()
                    .collectionName(collection)
                    .data(data)
                    .build());
            log.info("Milvus 子块向量写入完成: {} 条", data.size());
        } catch (Exception e) {
            log.error("Milvus 子块向量写入失败: {} 条", data.size(), e);
        }
    }

    @Override
    public void removeAll(List<Long> chunkIds) {
        if (!enabled || chunkIds == null || chunkIds.isEmpty()) {
            return;
        }
        if (!ensureCollection()) {
            return;
        }
        try {
            List<Object> ids = new ArrayList<>(chunkIds.size());
            for (Long id : chunkIds) {
                ids.add(String.valueOf(id));
            }
            milvusClient.delete(DeleteReq.builder()
                    .collectionName(collection)
                    .ids(ids)
                    .build());
            log.info("Milvus 子块向量删除完成: {} 条", ids.size());
        } catch (Exception e) {
            log.error("Milvus 子块向量删除失败: {} 条", chunkIds.size(), e);
        }
    }

    @Override
    public List<ChunkHit> search(String query, float threshold) {
        List<ChunkHit> hits = new ArrayList<>();
        if (!enabled || query == null || query.isBlank()) {
            return hits;
        }
        if (!ensureCollection()) {
            return hits;
        }
        try {
            List<Float> queryVector = embeddingService.embed(query);
            SearchResp resp = milvusClient.search(SearchReq.builder()
                    .collectionName(collection)
                    .annsField("embedding")
                    .data(List.of(new FloatVec(queryVector)))
                    .topK(recallTopK)
                    .filter("parent_id > 0")
                    .outputFields(List.of("chunk_id", "parent_id", "document_id", "content"))
                    .searchParams(Map.of("ef", searchEf))
                    .build());

            List<List<SearchResp.SearchResult>> groups = resp.getSearchResults();
            if (groups == null || groups.isEmpty()) {
                return hits;
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
                long parentId = toLong(entity.get("parent_id"));
                if (parentId <= 0) {
                    continue;
                }
                long chunkId = toLong(entity.get("chunk_id"));
                long documentId = toLong(entity.get("document_id"));
                hits.add(new ChunkHit(chunkId, parentId, documentId, text(entity.get("content")), score));
            }
            hits.sort(Comparator.comparingDouble(ChunkHit::similarity).reversed());
        } catch (Exception e) {
            log.error("Milvus 子块召回失败: query={}", query, e);
        }
        return hits;
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

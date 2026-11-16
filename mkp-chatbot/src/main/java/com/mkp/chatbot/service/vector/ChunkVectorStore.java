package com.mkp.chatbot.service.vector;

import com.mkp.common.entity.ChatbotChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.args.SortingOrder;
import redis.clients.jedis.search.Document;
import redis.clients.jedis.search.FTSearchParams;
import redis.clients.jedis.search.SearchResult;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档子块的向量读写。
 * 只存子块，命中后由 ChunkRetrievalService 回 MySQL 查父块。
 */
@Slf4j
@Service
public class ChunkVectorStore {

    private final JedisPooled jedisPooled;
    private final EmbeddingService embeddingService;
    private final ChunkVectorIndex chunkVectorIndex;

    public ChunkVectorStore(JedisPooled jedisPooled,
                            EmbeddingService embeddingService,
                            ChunkVectorIndex chunkVectorIndex) {
        this.jedisPooled = jedisPooled;
        this.embeddingService = embeddingService;
        this.chunkVectorIndex = chunkVectorIndex;
    }

    @Value("${mkp.knowledge.vector.chunk.recall-top-k:20}")
    private int recallTopK;

    /** 子块召回命中（只带定位信息，不带正文） */
    public record ChunkHit(long parentId, long documentId, float similarity) {
    }

    /**
     * 写入一条子块向量。embedding 的输入带上文档标题和小节标题，避免子块脱离上下文后语义跑偏。
     */
    public void write(ChatbotChunk chunk, String title, String category, List<Float> vector) {
        byte[] rawKey = (ChunkVectorIndex.CHUNK_KEY_PREFIX + chunk.getId())
                .getBytes(StandardCharsets.UTF_8);

        Map<byte[], byte[]> fields = new HashMap<>();
        fields.put(bytes("content"), bytes(chunk.getContent()));
        fields.put(bytes("sectionTitle"), bytes(chunk.getSectionTitle()));
        fields.put(bytes("title"), bytes(title));
        fields.put(bytes("category"), bytes(category));
        fields.put(bytes("documentId"), bytes(String.valueOf(chunk.getDocumentId())));
        fields.put(bytes("parentId"), bytes(String.valueOf(chunk.getParentId())));
        fields.put(bytes("seq"), bytes(String.valueOf(chunk.getSeq())));
        fields.put(bytes("embedding"), floatsToBytes(vector));

        jedisPooled.hset(rawKey, fields);
    }

    public void removeAll(List<Long> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return;
        }
        byte[][] keys = new byte[chunkIds.size()][];
        for (int i = 0; i < chunkIds.size(); i++) {
            keys[i] = (ChunkVectorIndex.CHUNK_KEY_PREFIX + chunkIds.get(i))
                    .getBytes(StandardCharsets.UTF_8);
        }
        jedisPooled.del(keys);
    }

    /**
     * 用问题去召回子块。
     *
     * @return 命中的子块列表（按相似度从高到低）；索引不可用时返回空列表
     */
    public List<ChunkHit> search(String query, float threshold) {
        List<ChunkHit> hits = new ArrayList<>();
        if (!chunkVectorIndex.ensureIndex()) {
            return hits;
        }
        try {
            List<Float> queryVec = embeddingService.embed(query);
            ByteBuffer buf = ByteBuffer.allocate(queryVec.size() * 4);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            queryVec.forEach(buf::putFloat);

            String queryExpr = "*=>[KNN " + recallTopK + " @embedding $BLOB AS score]";
            SearchResult result = jedisPooled.ftSearch(ChunkVectorIndex.CHUNK_INDEX, queryExpr,
                    FTSearchParams.searchParams()
                            .sortBy("score", SortingOrder.ASC)
                            .limit(0, recallTopK)
                            .returnFields("parentId", "documentId", "score")
                            .addParam("BLOB", buf.array())
                            .dialect(2));

            if (result == null || result.getTotalResults() == 0) {
                return hits;
            }

            for (Document doc : result.getDocuments()) {
                float distance = parseFloat(doc.getString("score"), 1.0f);
                float similarity = Math.max(0f, Math.min(1f, 1f - distance));
                if (similarity < threshold) {
                    continue;
                }
                long parentId = (long) parseFloat(doc.getString("parentId"), 0f);
                long documentId = (long) parseFloat(doc.getString("documentId"), 0f);
                if (parentId <= 0) {
                    continue;
                }
                hits.add(new ChunkHit(parentId, documentId, similarity));
            }
        } catch (Exception e) {
            log.error("子块向量召回失败: query={}", query, e);
        }
        return hits;
    }

    private float parseFloat(String s, float defaultValue) {
        if (s == null || s.isBlank()) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private byte[] bytes(String s) {
        return (s != null ? s : "").getBytes(StandardCharsets.UTF_8);
    }

    private byte[] floatsToBytes(List<Float> floats) {
        ByteBuffer buf = ByteBuffer.allocate(floats.size() * 4);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        for (float f : floats) {
            buf.putFloat(f);
        }
        return buf.array();
    }
}

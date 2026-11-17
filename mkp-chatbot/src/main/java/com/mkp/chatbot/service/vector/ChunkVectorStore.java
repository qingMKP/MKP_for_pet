package com.mkp.chatbot.service.vector;

import com.mkp.common.entity.ChatbotChunk;

import java.util.List;

/**
 * 文档子块的向量存取。
 *
 * 只存子块，命中后由 ChunkRetrievalService 按 parentId 回 MySQL 查父块整节。
 * 实现是 Milvus（collection mkp_knowledge_chunk，HNSW + COSINE）。
 */
public interface ChunkVectorStore {

    /**
     * 子块召回命中。
     *
     * @param chunkId    子块 id（MySQL mkp_chatbot_chunk.id）
     * @param parentId   父块 id，回 MySQL 查整节用
     * @param documentId 所属文档 id
     * @param content    子块正文，精排（rerank）时作为候选文本
     * @param similarity 当前排序分数：未开精排时是向量余弦相似度，开精排后是 rerank 相关性分
     */
    record ChunkHit(long chunkId, long parentId, long documentId, String content, float similarity) {
    }

    /**
     * 一条待写入的子块向量。
     *
     * @param chunk    子块实体，提供 id / documentId / parentId / seq / sectionTitle / content
     * @param title    文档标题，作为冗余字段写进向量库，便于排查
     * @param category 文档分类，同上
     * @param vector   1024 维向量，与 chunk 一一对应
     */
    record ChunkVector(ChatbotChunk chunk, String title, String category, List<Float> vector) {
    }

    /**
     * 写入一条子块向量。
     *
     * @param row 子块实体 + 向量
     */
    void write(ChunkVector row);

    /**
     * 批量写入。
     *
     * 默认实现逐条调用 write()；Milvus 实现覆盖了它，一批只发一次 upsert。
     *
     * @param rows 待写入的子块向量；为空时直接返回
     */
    default void writeBatch(List<ChunkVector> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (ChunkVector row : rows) {
            write(row);
        }
    }

    /** 删除子块向量（文档删除、重建索引时调用） */
    void removeAll(List<Long> chunkIds);

    /**
     * 用问题召回子块。
     *
     * @return 按相似度从高到低排好序的命中；存储不可用时返回空列表
     */
    List<ChunkHit> search(String query, float threshold);
}

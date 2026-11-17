package com.mkp.chatbot.service.vector;

import com.mkp.common.entity.ChatbotKnowledge;

import java.util.List;

/**
 * FAQ 短问答（mkp_chatbot_knowledge）的向量存取。
 *
 * 一条知识整条向量化，不切块；召回后直接把 keyword/answer/category 交给大模型。
 */
public interface KnowledgeVectorStore {

    /** 一条待写入的知识向量 */
    record KnowledgeVector(ChatbotKnowledge knowledge, List<Float> vector) {
    }

    /** 向量库是否可用；不可用时调用方降级为关键词匹配 */
    boolean isAvailable();

    /** 批量写入（upsert，按知识 id 覆盖） */
    void writeBatch(List<KnowledgeVector> rows);

    /** 删除知识向量 */
    void removeAll(List<Long> knowledgeIds);

    /**
     * 用问题召回知识。
     *
     * @return 按相似度从高到低排好序的命中；向量库不可用或无命中时返回空列表
     */
    List<KnowledgeMatch> search(String query, int topK, float threshold);
}

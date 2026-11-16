package com.mkp.chatbot.service.doc;

import com.mkp.chatbot.mapper.ChatbotChunkMapper;
import com.mkp.chatbot.mapper.ChatbotDocumentMapper;
import com.mkp.chatbot.service.vector.ChunkVectorStore;
import com.mkp.common.entity.ChatbotChunk;
import com.mkp.common.entity.ChatbotDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 父子块检索。
 *
 * 子块负责被召回（向量相似度），父块负责被返回（完整一节）。
 * 同一个父块命中多个子块时只保留最高分那条，避免同一段内容重复出现。
 */
@Slf4j
@Service
public class ChunkRetrievalService {

    private final ChunkVectorStore chunkVectorStore;
    private final ChatbotChunkMapper chunkMapper;
    private final ChatbotDocumentMapper documentMapper;

    @Value("${mkp.knowledge.vector.similarity-threshold:0.5}")
    private float threshold;

    @Value("${mkp.knowledge.vector.chunk.max-parents:4}")
    private int maxParents;

    @Value("${mkp.knowledge.vector.chunk.max-total-chars:3000}")
    private int maxTotalChars;

    public ChunkRetrievalService(ChunkVectorStore chunkVectorStore,
                                 ChatbotChunkMapper chunkMapper,
                                 ChatbotDocumentMapper documentMapper) {
        this.chunkVectorStore = chunkVectorStore;
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
    }

    /**
     * @return 拼好的长文档片段；没有命中返回空字符串
     */
    public String searchAsText(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        try {
            List<ChunkVectorStore.ChunkHit> hits = chunkVectorStore.search(query, threshold);
            if (hits.isEmpty()) {
                return "";
            }

            // 按 parentId 去重：hits 已按相似度降序，第一次出现就是最高分
            Map<Long, Float> parentScores = new LinkedHashMap<>();
            for (ChunkVectorStore.ChunkHit hit : hits) {
                parentScores.putIfAbsent(hit.parentId(), hit.similarity());
                if (parentScores.size() >= maxParents) {
                    break;
                }
            }

            List<ChatbotChunk> parents = chunkMapper.selectBatchIds(parentScores.keySet());
            if (parents.isEmpty()) {
                return "";
            }
            Map<Long, ChatbotChunk> parentMap = new HashMap<>();
            List<Long> docIds = new ArrayList<>();
            for (ChatbotChunk parent : parents) {
                parentMap.put(parent.getId(), parent);
                if (parent.getDocumentId() != null) {
                    docIds.add(parent.getDocumentId());
                }
            }
            Map<Long, String> titleMap = new HashMap<>();
            for (ChatbotDocument doc : documentMapper.selectBatchIds(docIds)) {
                titleMap.put(doc.getId(), doc.getTitle());
            }

            StringBuilder sb = new StringBuilder();
            for (Map.Entry<Long, Float> entry : parentScores.entrySet()) {
                ChatbotChunk parent = parentMap.get(entry.getKey());
                if (parent == null) {
                    continue;
                }
                if (sb.length() >= maxTotalChars) {
                    break;
                }
                String title = titleMap.getOrDefault(parent.getDocumentId(), "未知文档");
                String section = parent.getSectionTitle() == null ? "" : parent.getSectionTitle();
                sb.append("【文档：").append(title);
                if (!section.isBlank()) {
                    sb.append("｜小节：").append(section);
                }
                sb.append(String.format("｜相关度 %.0f%%】\n", entry.getValue() * 100));
                sb.append(truncate(parent.getContent(), maxTotalChars - sb.length())).append("\n\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.error("父子块检索失败: query={}", query, e);
            return "";
        }
    }

    private String truncate(String text, int remain) {
        if (text == null) {
            return "";
        }
        int limit = Math.max(remain, 200);
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit) + "…";
    }
}

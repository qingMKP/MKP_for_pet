package com.mkp.chatbot.service.doc;

import com.mkp.chatbot.mapper.ChatbotChunkMapper;
import com.mkp.chatbot.mapper.ChatbotDocumentMapper;
import com.mkp.chatbot.service.rerank.RerankService;
import com.mkp.chatbot.service.vector.ChunkVectorStore;
import com.mkp.common.entity.ChatbotChunk;
import com.mkp.common.entity.ChatbotDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
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
    private final RerankService rerankService;

    @Value("${mkp.knowledge.vector.similarity-threshold:0.5}")
    private float threshold;

    @Value("${mkp.knowledge.vector.chunk.max-parents:4}")
    private int maxParents;

    @Value("${mkp.knowledge.vector.chunk.max-total-chars:3000}")
    private int maxTotalChars;

    @Value("${mkp.knowledge.rerank.enabled:false}")
    private boolean rerankEnabled;

    /** 开精排后向量召回的宽松阈值：宁可多捞候选，交给精排筛 */
    @Value("${mkp.knowledge.rerank.recall-threshold:0.35}")
    private float rerankRecallThreshold;

    /** 精排分阈值，和向量余弦分不是一个尺度，单独配 */
    @Value("${mkp.knowledge.rerank.threshold:0.3}")
    private float rerankThreshold;

    public ChunkRetrievalService(ChunkVectorStore chunkVectorStore,
                                 ChatbotChunkMapper chunkMapper,
                                 ChatbotDocumentMapper documentMapper,
                                 RerankService rerankService) {
        this.chunkVectorStore = chunkVectorStore;
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
        this.rerankService = rerankService;
    }

    /**
     * @return 拼好的长文档片段；没有命中返回空字符串
     */
    public String searchAsText(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        try {
            float recallThreshold = rerankEnabled ? rerankRecallThreshold : threshold;
            List<ChunkVectorStore.ChunkHit> hits = chunkVectorStore.search(query, recallThreshold);
            if (hits.isEmpty()) {
                return "";
            }
            if (rerankEnabled) {
                hits = rerank(query, hits);
                if (hits.isEmpty()) {
                    return "";
                }
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

    /**
     * 精排：对召回的子块逐条打 (query, 子块正文) 相关性分，替换向量分并重新排序。
     * rerank 不可用时返回原候选，保证检索链路不中断。
     */
    private List<ChunkVectorStore.ChunkHit> rerank(String query, List<ChunkVectorStore.ChunkHit> hits) {
        if (hits.size() <= 1) {
            return hits;
        }
        List<String> documents = new ArrayList<>(hits.size());
        for (ChunkVectorStore.ChunkHit hit : hits) {
            documents.add(hit.content() == null ? "" : hit.content());
        }
        List<Float> scores = rerankService.score(query, documents);
        if (scores == null) {
            log.warn("精排不可用，沿用向量分排序: query={}", query);
            return hits;
        }

        List<ChunkVectorStore.ChunkHit> ranked = new ArrayList<>(hits.size());
        for (int i = 0; i < hits.size(); i++) {
            float score = scores.get(i);
            if (score < rerankThreshold) {
                continue;
            }
            ChunkVectorStore.ChunkHit hit = hits.get(i);
            ranked.add(new ChunkVectorStore.ChunkHit(
                    hit.chunkId(), hit.parentId(), hit.documentId(), hit.content(), score));
        }
        ranked.sort(Comparator.comparingDouble(ChunkVectorStore.ChunkHit::similarity).reversed());
        log.info("精排完成: 候选={} 保留={} query={}", hits.size(), ranked.size(), query);
        return ranked;
    }
}

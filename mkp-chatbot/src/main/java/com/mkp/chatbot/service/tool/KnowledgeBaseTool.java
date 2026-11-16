package com.mkp.chatbot.service.tool;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mkp.chatbot.mapper.ChatbotKnowledgeMapper;
import com.mkp.chatbot.service.doc.ChunkRetrievalService;
import com.mkp.chatbot.service.react.ReActContext;
import com.mkp.chatbot.service.react.Tool;
import com.mkp.chatbot.service.vector.EmbeddingService;
import com.mkp.chatbot.service.vector.KnowledgeMatch;
import com.mkp.chatbot.service.vector.VectorIndexManager;
import com.mkp.common.entity.ChatbotKnowledge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
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
import java.util.concurrent.TimeUnit;

/**
 * 知识库检索工具。
 *
 * 一次调用并行查两个库，结果拼成一段文字返回：
 * 1. 长文档父子块：子块向量召回 → 按 parentId 回查父块整节（ChunkRetrievalService）
 * 2. 短问答：knowledge 表整条向量召回（idx:knowledge:vector）
 * 两条都没有命中时，降级到 keyword 字段的字面包含匹配。
 */
@Slf4j
@Component
public class KnowledgeBaseTool implements Tool {

    private static final String KNOWLEDGE_CACHE_KEY = "chatbot:knowledge:all";

    // ========== 短问答（knowledge 表） ==========
    @Autowired
    private ChatbotKnowledgeMapper knowledgeMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // ========== 向量检索 ==========
    @Autowired
    private JedisPooled jedisPooled;
    @Autowired
    private EmbeddingService embeddingService;
    @Autowired
    private VectorIndexManager indexManager;

    // ========== 长文档父子块检索 ==========
    @Autowired
    private ChunkRetrievalService chunkRetrievalService;

    @Value("${mkp.knowledge.vector.top-k:10}")
    private int topK;

    @Value("${mkp.knowledge.vector.similarity-threshold:0.5}")
    private float threshold;

    @Value("${mkp.knowledge.vector.enabled:true}")
    private boolean vectorEnabled;

    @Value("${mkp.knowledge.vector.chunk.enabled:true}")
    private boolean chunkEnabled;

    @Override
    public String getName() {
        return "search_knowledge";
    }

    @Override
    public String getDescription() {
        return "搜索宠物知识库，获取专业的宠物养护、疾病、喂养知识，以及长文档（饲养指南、健康手册）中的详细章节。" +
               "当用户询问宠物健康、饮食、行为、护理等专业问题时使用。";
    }

    @Override
    public Map<String, String> getParametersSchema() {
        Map<String, String> schema = new HashMap<>();
        schema.put("keyword", "搜索关键词，描述用户问题的核心内容，如'幼犬喂食'、'猫咪驱虫'");
        return schema;
    }

    @Override
    public String execute(Map<String, Object> parameters, ReActContext context) {
        String keyword = parameters.get("keyword") != null
                ? parameters.get("keyword").toString() : "";

        if (keyword.isBlank()) {
            return "请提供搜索关键词";
        }

        log.info("执行知识库检索: keyword={}, vectorEnabled={}, chunkEnabled={}",
                keyword, vectorEnabled, chunkEnabled);

        // 第一路：长文档父子块（子块召回 → 父块整节）
        String docBlock = "";
        if (chunkEnabled) {
            docBlock = chunkRetrievalService.searchAsText(keyword);
        }

        // 第二路：短问答整条向量召回
        if (vectorEnabled) {
            List<KnowledgeMatch> matches = vectorSearch(keyword);
            if (matches == null) {
                log.info("向量检索不可用，降级到关键词匹配: {}", keyword);
                return docBlock.isBlank() ? keywordSearch(keyword) : docBlock;
            }
            if (!matches.isEmpty()) {
                String faq = formatWithSimilarity(matches);
                return docBlock.isBlank() ? faq : docBlock + "\n\n" + faq;
            }
            if (!docBlock.isBlank()) {
                return docBlock;
            }
            if (keyword.length() > 4) {
                return "未找到与 '" + keyword + "' 相关的知识库内容。请尝试用更简短的关键词重新搜索。";
            }
            return "未找到与 '" + keyword + "' 相关的知识库内容。";
        }

        if (!docBlock.isBlank()) {
            return docBlock;
        }
        return keywordSearch(keyword);
    }

    // ==================== 向量检索（短问答） ====================

    /**
     * @return 匹配结果列表；空列表 = 无匹配；null = 检索不可用需降级
     */
    private List<KnowledgeMatch> vectorSearch(String query) {
        if (!indexManager.ensureIndex()) {
            return null;
        }

        try {
            List<Float> queryVec = embeddingService.embed(query);

            ByteBuffer buf = ByteBuffer.allocate(queryVec.size() * 4);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            queryVec.forEach(buf::putFloat);
            byte[] blob = buf.array();

            String queryExpr = "*=>[KNN " + topK + " @embedding $BLOB AS score]";
            SearchResult result = jedisPooled.ftSearch(VectorIndexManager.VECTOR_INDEX, queryExpr,
                    FTSearchParams.searchParams()
                            .sortBy("score", SortingOrder.ASC)
                            .limit(0, topK)
                            .returnFields("keyword", "answer", "category", "score")
                            .addParam("BLOB", blob)
                            .dialect(2));

            return parseSearchResult(result);

        } catch (Exception e) {
            log.error("向量检索失败，降级到关键词匹配: query={}", query, e);
            return null;
        }
    }

    /**
     * COSINE 距离越小越相似，相似度 = 1 - 距离。
     */
    private List<KnowledgeMatch> parseSearchResult(SearchResult result) {
        List<KnowledgeMatch> matches = new ArrayList<>();
        if (result == null || result.getTotalResults() == 0) {
            return matches;
        }

        for (Document doc : result.getDocuments()) {
            float distance = 1.0f;
            String scoreStr = doc.getString("score");
            if (scoreStr != null) {
                try {
                    distance = Float.parseFloat(scoreStr);
                } catch (NumberFormatException ignored) {
                }
            }
            float similarity = Math.max(0f, Math.min(1f, 1f - distance));

            if (similarity < threshold) {
                continue;
            }

            Object rawId = doc.getId();
            String keyStr = rawId instanceof byte[]
                    ? new String((byte[]) rawId, StandardCharsets.UTF_8)
                    : String.valueOf(rawId);
            long id;
            try {
                id = Long.parseLong(keyStr.replace(VectorIndexManager.VECTOR_KEY_PREFIX, ""));
            } catch (NumberFormatException e) {
                log.warn("无法从向量 key 解析知识 ID: {}", keyStr);
                continue;
            }

            matches.add(new KnowledgeMatch(id,
                    nvl(doc.getString("keyword")),
                    nvl(doc.getString("answer")),
                    nvl(doc.getString("category")),
                    similarity));
        }

        return matches;
    }

    private String nvl(String s) {
        return s != null ? s : "";
    }

    // ==================== 关键词兜底 ====================

    private String keywordSearch(String query) {
        try {
            List<ChatbotKnowledge> allKnowledge = loadKnowledge();
            List<ChatbotKnowledge> matched = new ArrayList<>();

            for (ChatbotKnowledge entry : allKnowledge) {
                String[] keywords = entry.getKeyword().split(",");
                for (String kw : keywords) {
                    if (query.toLowerCase().contains(kw.trim().toLowerCase())) {
                        matched.add(entry);
                        break;
                    }
                }
            }

            if (matched.isEmpty()) {
                return "未找到与 '" + query + "' 相关的知识库内容。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("找到 ").append(matched.size()).append(" 条相关知识：\n\n");

            for (int i = 0; i < matched.size(); i++) {
                ChatbotKnowledge k = matched.get(i);
                sb.append(i + 1).append(". 【")
                        .append(k.getCategory() != null ? k.getCategory() : "通用")
                        .append("】").append(k.getKeyword()).append("\n");
                sb.append("   ").append(k.getAnswer()).append("\n\n");
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("知识库检索失败", e);
            return "知识库检索失败: " + e.getMessage();
        }
    }

    private List<ChatbotKnowledge> loadKnowledge() {
        String json = stringRedisTemplate.opsForValue().get(KNOWLEDGE_CACHE_KEY);
        if (json != null) {
            return JSONUtil.toList(json, ChatbotKnowledge.class);
        }
        List<ChatbotKnowledge> list = knowledgeMapper.selectList(
                new LambdaQueryWrapper<ChatbotKnowledge>()
                        .eq(ChatbotKnowledge::getDeleted, 0));
        try {
            stringRedisTemplate.opsForValue().set(KNOWLEDGE_CACHE_KEY,
                    JSONUtil.toJsonStr(list), 30, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("写入知识库缓存失败", e);
        }
        return list;
    }

    // ==================== 结果格式化 ====================

    private String formatWithSimilarity(List<KnowledgeMatch> matches) {
        StringBuilder result = new StringBuilder("找到以下相关知识库内容：\n\n");
        for (int i = 0; i < matches.size(); i++) {
            KnowledgeMatch m = matches.get(i);
            result.append(String.format("【结果%d，相关度: %.0f%%】\n",
                    i + 1, m.getSimilarity() * 100));
            if (m.getCategory() != null && !m.getCategory().isBlank()) {
                result.append("分类: ").append(m.getCategory()).append("\n");
            }
            result.append("关键词: ").append(m.getKeyword()).append("\n");
            result.append("内容: ").append(m.getAnswer()).append("\n\n");
        }
        return result.toString().trim();
    }
}

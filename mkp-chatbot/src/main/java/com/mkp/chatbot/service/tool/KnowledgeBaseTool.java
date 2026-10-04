package com.mkp.chatbot.service.tool;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mkp.chatbot.mapper.ChatbotKnowledgeMapper;
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
 * 知识库检索工具
 *
 * 支持双模式检索：
 * 1. 向量语义检索（Redis Stack + DashScope Embedding）——默认启用，语义匹配
 * 2. 关键词包含匹配（向量不可用时自动降级）——兜底方案，字符串匹配
 *
 * 启动时检测 Redis 是否支持 RediSearch 模块，不支持则自动使用关键词匹配，
 * 保证任何环境下系统都能正常运行。
 */
@Slf4j
@Component
public class KnowledgeBaseTool implements Tool {

    private static final String KNOWLEDGE_CACHE_KEY = "chatbot:knowledge:all";

    // ========== 旧方案依赖（关键词匹配 + Redis 全量缓存，同时作为降级兜底） ==========
    @Autowired
    private ChatbotKnowledgeMapper knowledgeMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // ========== 向量方案依赖 ==========
    @Autowired
    private JedisPooled jedisPooled;
    @Autowired
    private EmbeddingService embeddingService;
    @Autowired
    private VectorIndexManager indexManager;

    @Value("${mkp.knowledge.vector.top-k:5}")
    private int topK;

    @Value("${mkp.knowledge.vector.similarity-threshold:0.75}")
    private float threshold;

    @Value("${mkp.knowledge.vector.enabled:true}")
    private boolean vectorEnabled;

    @Override
    public String getName() {
        return "search_knowledge";
    }

    @Override
    public String getDescription() {
        return "搜索宠物知识库，获取专业的宠物养护、疾病、饲养知识。" +
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
        String keyword = parameters.get("keyword") != null ?
                parameters.get("keyword").toString() : "";

        if (keyword.isBlank()) {
            return "请提供搜索关键词";
        }

        log.info("执行知识库搜索: keyword={}, vectorEnabled={}", keyword, vectorEnabled);

        // ========== 路径 1：向量语义检索 ==========
        if (vectorEnabled) {
            List<KnowledgeMatch> matches = vectorSearch(keyword);
            if (matches != null) {
                if (!matches.isEmpty()) {
                    return formatWithSimilarity(matches);
                }
                // 向量检索无结果，引导 LLM 换更短关键词重试
                if (keyword.length() > 4) {
                    return "未找到与 '" + keyword + "' 相关的知识库内容。请尝试用更简短的关键词重新搜索。";
                }
                return "未找到与 '" + keyword + "' 相关的知识库内容。";
            }
            // vectorSearch 返回 null = 向量检索不可用，走降级
            log.info("向量检索不可用，降级到关键词匹配: {}", keyword);
        }

        // ========== 路径 2：关键词包含匹配（原逻辑 / 降级） ==========
        return keywordSearch(keyword);
    }

    // ==================== 向量检索 ====================

    /**
     * 基于 Redis Stack 的向量语义检索
     *
     * @return 匹配结果列表；空列表 = 无匹配；null = 检索不可用需降级
     */
    private List<KnowledgeMatch> vectorSearch(String query) {
        // 确保索引已就绪（Redis 不支持 RediSearch 时返回 false）
        if (!indexManager.ensureIndex()) {
            return null;
        }

        try {
            // Step 1: 查询文本 → Embedding 向量
            List<Float> queryVec = embeddingService.embed(query);

            // Step 2: float[] → 4 字节小端序 byte[]（Redis FLOAT32 格式）
            ByteBuffer buf = ByteBuffer.allocate(queryVec.size() * 4);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            queryVec.forEach(buf::putFloat);
            byte[] blob = buf.array();

            // Step 3: Redis FT.SEARCH KNN 余弦相似度检索
            String queryExpr = "*=>[KNN " + topK + " @embedding $BLOB AS score]";
            SearchResult result = jedisPooled.ftSearch(VectorIndexManager.VECTOR_INDEX, queryExpr,
                    FTSearchParams.searchParams()
                            .sortBy("score", SortingOrder.ASC)
                            .limit(0, topK)
                            .returnFields("keyword", "answer", "category", "score")
                            .addParam("BLOB", blob)
                            .dialect(2));

            // Step 4: 解析结果并过滤低相似度
            return parseSearchResult(result);

        } catch (Exception e) {
            log.error("向量检索失败，降级到关键词匹配: query={}", query, e);
            return null;
        }
    }

    /**
     * 解析 FT.SEARCH 返回结果
     * COSINE 距离越小越相似，相似度 = 1 - 距离
     */
    private List<KnowledgeMatch> parseSearchResult(SearchResult result) {
        List<KnowledgeMatch> matches = new ArrayList<>();
        if (result == null || result.getTotalResults() == 0) {
            return matches;
        }

        for (Document doc : result.getDocuments()) {
            // 解析相似度分数
            float distance = 1.0f;
            String scoreStr = doc.getString("score");
            if (scoreStr != null) {
                try {
                    distance = Float.parseFloat(scoreStr);
                } catch (NumberFormatException ignored) {
                }
            }
            float similarity = Math.max(0f, Math.min(1f, 1f - distance));

            // 过滤低相似度结果
            if (similarity < threshold) {
                continue;
            }

            // 从 Redis key 中解析知识 ID（key 格式：knowledge:vector:{id}）
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

    // ==================== 关键词匹配（原逻辑 / 降级方案） ====================

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
            log.error("知识库搜索失败", e);
            return "知识库搜索失败: " + e.getMessage();
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

    /**
     * 向量检索结果格式化（附带相似度分数，帮助 LLM 判断相关性）
     */
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

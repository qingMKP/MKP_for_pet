package com.mkp.chatbot.service.rerank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * RAG 精排（rerank）。
 *
 * 放在"向量召回之后、父块去重之前"：对召回回来的子块正文逐条打 (query, 子块) 相关性分，
 * 用这个分替换向量分重新排序，解决"语义相近但答非所问"的候选排在前面。
 *
 * 走 DashScope 的 rerank 接口（默认 gte-rerank-v2），与项目现有 embedding 用同一个 API Key。
 * 任何失败都返回 null，由调用方降级为向量分排序，不影响问答可用性。
 */
@Slf4j
@Service
public class RerankService {

    private static final String DEFAULT_ENDPOINT =
            "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RestTemplate restTemplate;

    @Value("${mkp.knowledge.rerank.enabled:false}")
    private boolean enabled;

    @Value("${mkp.knowledge.rerank.endpoint:}")
    private String endpoint;

    @Value("${mkp.knowledge.rerank.model:gte-rerank-v2}")
    private String model;

    @Value("${mkp.knowledge.rerank.timeout-ms:2000}")
    private int timeoutMs;

    @Value("${spring.ai.dashscope.api-key:}")
    private String apiKey;

    @PostConstruct
    public void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 给候选文本逐条打相关性分。
     *
     * @param query     用户问题（或改写后的检索词）
     * @param documents 候选子块正文，顺序与返回值一一对应
     * @return 与 documents 等长的分数列表，范围 0~1；未启用、未配 key 或调用失败时返回 null
     */
    public List<Float> score(String query, List<String> documents) {
        if (!enabled || query == null || query.isBlank() || documents == null || documents.isEmpty()) {
            return null;
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("rerank 未配置 DashScope API Key，跳过精排");
            return null;
        }

        try {
            Map<String, Object> input = Map.of("query", query, "documents", documents);
            Map<String, Object> parameters = Map.of(
                    "return_documents", false,
                    "top_n", documents.size());
            Map<String, Object> body = Map.of(
                    "model", model,
                    "input", input,
                    "parameters", parameters);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            String url = (endpoint == null || endpoint.isBlank()) ? DEFAULT_ENDPOINT : endpoint;
            long start = System.currentTimeMillis();
            String response = restTemplate.postForObject(url, new HttpEntity<>(body, headers), String.class);
            List<Float> scores = parse(response, documents.size());
            log.info("rerank 完成: model={} 候选={} 耗时={}ms", model, documents.size(),
                    System.currentTimeMillis() - start);
            return scores;
        } catch (Exception e) {
            log.warn("rerank 调用失败，降级为向量分排序: {}", e.toString());
            return null;
        }
    }

    /**
     * 解析 DashScope 返回：{"output":{"results":[{"index":0,"relevance_score":0.51}, ...]}}
     */
    private List<Float> parse(String response, int size) throws Exception {
        if (response == null || response.isBlank()) {
            return null;
        }
        JsonNode results = objectMapper.readTree(response).path("output").path("results");
        if (!results.isArray() || results.isEmpty()) {
            return null;
        }
        List<Float> scores = new ArrayList<>(Collections.nCopies(size, 0f));
        for (JsonNode result : results) {
            int index = result.path("index").asInt(-1);
            if (index >= 0 && index < size) {
                scores.set(index, (float) result.path("relevance_score").asDouble(0d));
            }
        }
        return scores;
    }
}

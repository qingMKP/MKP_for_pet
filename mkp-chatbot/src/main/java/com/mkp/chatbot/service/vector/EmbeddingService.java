package com.mkp.chatbot.service.vector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DashScope Embedding API 调用服务（阿里云百炼）
 *
 * 接口兼容 OpenAI 格式：POST {base-url}/compatible-mode/v1/embeddings
 * 将文本转为向量，用于向量相似度检索
 */
@Slf4j
@Service
public class EmbeddingService {

    @Value("${dashscope.api.key}")
    private String apiKey;

    @Value("${dashscope.api.base-url:https://dashscope.aliyuncs.com}")
    private String baseUrl;

    @Value("${mkp.knowledge.vector.embedding-model:text-embedding-v3}")
    private String model;

    @Value("${mkp.knowledge.vector.embedding-dim:1024}")
    private int dim;

    private final RestTemplate restTemplate;

    public EmbeddingService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 将文本转为 Embedding 向量
     *
     * @param text 待转换的文本
     * @return 1024 维 float 向量
     */
    public List<Float> embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    /**
     * 批量转换（用于全量同步时减少 API 调用次数）
     * DashScope text-embedding-v3 单次批量上限 10 条，这里按批分片
     */
    public List<List<Float>> embedBatch(List<String> texts) {
        List<List<Float>> allResults = new ArrayList<>();
        int batchSize = 10; // DashScope 批量上限

        for (int start = 0; start < texts.size(); start += batchSize) {
            List<String> batch = texts.subList(start, Math.min(start + batchSize, texts.size()));
            allResults.addAll(doEmbed(batch));
        }
        return allResults;
    }

    private List<List<Float>> doEmbed(List<String> batch) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("DASHSCOPE_API_KEY 未配置，无法调用 Embedding API");
        }

        String url = baseUrl + "/compatible-mode/v1/embeddings";

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("input", batch);
        body.put("dimensions", dim); // 指定输出维度（1024）

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);

            if (response == null || !response.containsKey("data")) {
                log.error("Embedding API 返回为空: response={}", response);
                throw new RuntimeException("Embedding API 返回异常");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");

            // 按 index 排序确保与输入顺序一致
            data.sort(Comparator.comparingInt(m -> ((Number) m.get("index")).intValue()));

            return data.stream().map(item -> {
                @SuppressWarnings("unchecked")
                List<Double> embedding = (List<Double>) item.get("embedding");
                return embedding.stream()
                        .map(Double::floatValue)
                        .collect(Collectors.toList());
            }).collect(Collectors.toList());

        } catch (Exception e) {
            log.error("调用 DashScope Embedding API 失败, batchSize={}", batch.size(), e);
            throw new RuntimeException("Embedding 调用失败", e);
        }
    }
}

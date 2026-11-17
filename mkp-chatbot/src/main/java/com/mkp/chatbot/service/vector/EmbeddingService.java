package com.mkp.chatbot.service.vector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本向量化服务。
 *
 * 底层走 Spring AI 的 EmbeddingModel（DashScope text-embedding-v3，1024 维）。
 * embedBatch 保留原有的按批切片逻辑：DashScope 单请求最多 10 条 input，
 * 且返回结果必须与入参顺序一一对应。
 */
@Slf4j
@Service
public class EmbeddingService {

    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;

    @Value("${mkp.ai.embedding.batch-size:10}")
    private int batchSize;

    public EmbeddingService(ObjectProvider<EmbeddingModel> embeddingModelProvider) {
        this.embeddingModelProvider = embeddingModelProvider;
    }

    /**
     * 单条文本转向量。
     */
    public List<Float> embed(String text) {
        return toList(requireModel().embed(text));
    }

    /**
     * 批量转向量，返回顺序与入参严格一致。
     *
     * 内部按 mkp.ai.embedding.batch-size（默认 10）切片，逐批调用 DashScope，
     * 每批结果按原顺序追加，所以调用方可以直接用下标把 texts.get(i) 和结果对应起来。
     *
     * @param texts 待向量化文本，允许为 null 或空
     * @return 与入参等长、顺序一致的向量列表；入参为空时返回空列表
     */
    public List<List<Float>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }

        int size = batchSize > 0 ? batchSize : 10;
        List<List<Float>> allResults = new ArrayList<>(texts.size());

        for (int start = 0; start < texts.size(); start += size) {
            List<String> batch = texts.subList(start, Math.min(start + size, texts.size()));
            List<float[]> vectors = requireModel().embed(batch);
            for (float[] vector : vectors) {
                allResults.add(toList(vector));
            }
        }
        return allResults;
    }

    private EmbeddingModel requireModel() {
        EmbeddingModel model = embeddingModelProvider.getIfAvailable();
        if (model == null) {
            throw new IllegalStateException("EmbeddingModel 未配置，请设置 DASHSCOPE_API_KEY");
        }
        return model;
    }

    private List<Float> toList(float[] vector) {
        List<Float> result = new ArrayList<>(vector.length);
        for (float value : vector) {
            result.add(value);
        }
        return result;
    }
}

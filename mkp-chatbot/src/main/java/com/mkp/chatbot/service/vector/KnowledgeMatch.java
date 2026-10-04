package com.mkp.chatbot.service.vector;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 向量检索匹配结果
 */
@Data
@AllArgsConstructor
public class KnowledgeMatch {
    /** 知识库ID */
    private Long id;
    /** 关键词（原始录入） */
    private String keyword;
    /** 知识答案 */
    private String answer;
    /** 分类 */
    private String category;
    /** 余弦相似度（0~1，越大越相似） */
    private float similarity;
}

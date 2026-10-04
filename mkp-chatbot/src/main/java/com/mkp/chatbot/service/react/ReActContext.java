package com.mkp.chatbot.service.react;

import com.mkp.common.dto.ChatbotPostVO;
import com.mkp.common.dto.PostingVO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * ReAct 推理上下文
 *
 * 用于在推理过程中传递辅助信息（如相关帖子列表）
 */
@Data
public class ReActContext {

    /**
     * 本轮推理收集到的相关社区帖子
     * 供前端展示参考
     */
    private List<ChatbotPostVO> relatedPosts = new ArrayList<>();

    /**
     * 本轮推理收集到的交易广场帖子（领养/购买）
     * 供前端展示可点击卡片
     */
    private List<PostingVO> relatedPostings = new ArrayList<>();

    public void addRelatedPosts(List<ChatbotPostVO> posts) {
        if (posts != null && !posts.isEmpty()) {
            // 去重后追加
            for (ChatbotPostVO post : posts) {
                boolean exists = relatedPosts.stream()
                        .anyMatch(p -> p.getId() != null && p.getId().equals(post.getId()));
                if (!exists) {
                    relatedPosts.add(post);
                }
            }
        }
    }

    public void addRelatedPostings(List<PostingVO> postings) {
        if (postings != null && !postings.isEmpty()) {
            for (PostingVO posting : postings) {
                boolean exists = relatedPostings.stream()
                        .anyMatch(p -> p.getId() != null && p.getId().equals(posting.getId()));
                if (!exists) {
                    relatedPostings.add(posting);
                }
            }
        }
    }
}

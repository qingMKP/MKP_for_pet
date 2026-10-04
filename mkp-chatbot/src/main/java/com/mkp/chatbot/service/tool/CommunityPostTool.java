package com.mkp.chatbot.service.tool;

import com.mkp.api.PostFeignClient;
import com.mkp.chatbot.service.react.ReActContext;
import com.mkp.chatbot.service.react.Tool;
import com.mkp.common.dto.ChatbotPostVO;
import com.mkp.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 社区帖子搜索工具
 *
 * 功能：搜索社区帖子，获取其他宠主的经验分享
 */
@Component
@Slf4j
public class CommunityPostTool implements Tool {

    @Autowired
    private PostFeignClient postFeignClient;

    @Override
    public String getName() {
        return "search_posts";
    }

    @Override
    public String getDescription() {
        return "搜索社区帖子，获取其他宠物主人的经验分享和讨论。" +
               "当用户想了解真实案例、经验分享、社区讨论时使用。";
    }

    @Override
    public Map<String, String> getParametersSchema() {
        Map<String, String> schema = new HashMap<>();
        schema.put("keyword", "搜索关键词，描述要查找的帖子内容");
        return schema;
    }

    @Override
    public String execute(Map<String, Object> parameters, ReActContext context) {
        String keyword = parameters.get("keyword") != null ?
            parameters.get("keyword").toString() : "";

        if (keyword.isBlank()) {
            return "请提供搜索关键词";
        }

        log.info("执行社区帖子搜索: keyword={}", keyword);

        try {
            R<List<ChatbotPostVO>> result = postFeignClient.searchPosts(keyword);

            if (result.getCode() != 200 || result.getData() == null || result.getData().isEmpty()) {
                return "社区中未找到与 '" + keyword + "' 相关的帖子。";
            }

            List<ChatbotPostVO> posts = result.getData();

            // 将帖子写入上下文，供前端展示
            context.addRelatedPosts(posts);

            StringBuilder sb = new StringBuilder();
            sb.append("找到 ").append(posts.size()).append(" 篇相关帖子：\n\n");

            for (int i = 0; i < posts.size(); i++) {
                ChatbotPostVO post = posts.get(i);
                sb.append(i + 1).append(". ").append(truncate(post.getContent(), 100)).append("\n");
                sb.append("   点赞: ").append(post.getLikeCount()).append("\n\n");
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("社区帖子搜索失败", e);
            return "社区帖子搜索失败: " + e.getMessage();
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}

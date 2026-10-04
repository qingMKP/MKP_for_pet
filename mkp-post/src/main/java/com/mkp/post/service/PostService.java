package com.mkp.post.service;

import com.mkp.common.dto.ChatbotPostVO;
import com.mkp.common.dto.PageResult;
import com.mkp.common.dto.PostSaveDTO;
import com.mkp.common.dto.PostVO;
import com.mkp.common.result.R;

import java.util.List;
import java.util.Map;

public interface PostService {
    R<List<PostVO>> listPosts(Integer page, Integer size, Long currentUserId);
    R<PostVO> getPostDetail(Long postId, Long currentUserId);
    R<PostVO> createPost(Long userId, PostSaveDTO dto);
    R<Void> deletePost(Long userId, Long postId);
    R<Boolean> toggleLike(Long userId, Long postId);
    R<List<ChatbotPostVO>> searchPosts(String keyword);

    /**
     * 社区广场搜索：按帖子文案 + 作者昵称检索，可叠加「有无配图 / #话题」筛选
     * （ES 优先，ES 不可用时降级 MySQL LIKE）。
     *
     * @param sort     relevance（相关度）/ latest（最新）/ hot（最热）
     * @param hasImage true 只看有图 / false 只看纯文字 / null 不限
     * @param topic    #话题 精确筛选；keyword 以 # 开头时也会被当成话题
     */
    R<PageResult<PostVO>> searchPosts(String keyword, Integer page, Integer size, String sort,
                                      Boolean hasImage, String topic, Long currentUserId);

    // Admin
    R<PageResult<PostVO>> adminListPosts(Integer page, Integer size, String keyword);
    R<Void> adminDeletePost(Long postId);
    R<Map<String, Object>> adminGetPostStats();
    R<List<Map<String, Object>>> adminGetDailyTrend();

    // 搜索索引维护
    R<Long> rebuildSearchIndex();
    R<Long> refreshAuthorIndex(Long userId);
    R<String> getSearchStatus();
}

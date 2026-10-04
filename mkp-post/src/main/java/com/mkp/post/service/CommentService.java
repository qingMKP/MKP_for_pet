package com.mkp.post.service;

import com.mkp.common.dto.CommentSaveDTO;
import com.mkp.common.dto.CommentVO;
import com.mkp.common.dto.PageResult;
import com.mkp.common.result.R;

import java.util.List;
import java.util.Map;

public interface CommentService {
    R<List<CommentVO>> listComments(Long postId, Long currentUserId);
    R<CommentVO> createComment(Long userId, Long postId, CommentSaveDTO dto);
    R<Boolean> toggleLike(Long userId, Long commentId);
    R<Void> deleteComment(Long userId, Long commentId);

    // Admin
    R<PageResult<Map<String, Object>>> adminListComments(Integer page, Integer size, String keyword);
    R<Void> adminDeleteComment(Long commentId);
    R<Map<String, Object>> adminGetCommentStats();
}

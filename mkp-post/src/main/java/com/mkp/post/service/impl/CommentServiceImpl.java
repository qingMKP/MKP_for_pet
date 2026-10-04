package com.mkp.post.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mkp.common.dto.CommentSaveDTO;
import com.mkp.common.dto.CommentVO;
import com.mkp.common.dto.PageResult;
import com.mkp.common.dto.UserVO;
import com.mkp.common.entity.Comment;
import com.mkp.common.entity.CommentLike;
import com.mkp.common.result.R;
import com.mkp.post.mapper.CommentLikeMapper;
import com.mkp.post.mapper.CommentMapper;
import com.mkp.post.service.CommentService;
import com.mkp.post.utils.UserCacheService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CommentServiceImpl implements CommentService {

    private final CommentMapper commentMapper;
    private final CommentLikeMapper commentLikeMapper;
    private final UserCacheService userCacheService;

    public CommentServiceImpl(CommentMapper commentMapper,
                              CommentLikeMapper commentLikeMapper,
                              UserCacheService userCacheService) {
        this.commentMapper = commentMapper;
        this.commentLikeMapper = commentLikeMapper;
        this.userCacheService = userCacheService;
    }

    @Override
    public R<List<CommentVO>> listComments(Long postId, Long currentUserId) {
        List<Comment> all = commentMapper.selectList(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getPostId, postId)
                        .orderByAsc(Comment::getCreateTime));

        List<CommentVO> topLevel = new ArrayList<>();
        List<CommentVO> allVos = all.stream()
                .map(c -> toVO(c, currentUserId))
                .collect(Collectors.toList());

        // Separate top-level comments and replies
        for (CommentVO vo : allVos) {
            if (vo.getParentId() == null) {
                topLevel.add(vo);
            }
        }

        // Attach replies to their parent
        for (CommentVO vo : allVos) {
            if (vo.getParentId() != null) {
                for (CommentVO parent : topLevel) {
                    if (parent.getId().equals(vo.getParentId())) {
                        if (parent.getReplies() == null) {
                            parent.setReplies(new ArrayList<>());
                        }
                        parent.getReplies().add(vo);
                        break;
                    }
                }
            }
        }

        // Sort replies chronologically
        for (CommentVO parent : topLevel) {
            if (parent.getReplies() != null) {
                parent.getReplies().sort(Comparator.comparing(CommentVO::getCreateTime));
            }
        }

        // Sort top-level: by likeCount DESC, then createTime DESC
        topLevel.sort(Comparator
                .comparing(CommentVO::getLikeCount, Comparator.reverseOrder())
                .thenComparing(CommentVO::getCreateTime, Comparator.reverseOrder()));

        return R.ok(topLevel);
    }

    @Override
    @Transactional
    public R<CommentVO> createComment(Long userId, Long postId, CommentSaveDTO dto) {
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setContent(dto.getContent());
        comment.setParentId(dto.getParentId());
        comment.setLikeCount(0);
        commentMapper.insert(comment);
        return R.ok(toVO(comment, userId));
    }

    @Override
    @Transactional
    public R<Boolean> toggleLike(Long userId, Long commentId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            return R.fail(404, "Comment not found");
        }
        CommentLike existing = commentLikeMapper.selectOne(
                new LambdaQueryWrapper<CommentLike>()
                        .eq(CommentLike::getCommentId, commentId)
                        .eq(CommentLike::getUserId, userId));
        if (existing != null) {
            commentLikeMapper.deleteById(existing.getId());
            if (comment.getLikeCount() > 0) {
                comment.setLikeCount(comment.getLikeCount() - 1);
                commentMapper.updateById(comment);
            }
            return R.ok(false);
        } else {
            CommentLike like = new CommentLike();
            like.setCommentId(commentId);
            like.setUserId(userId);
            commentLikeMapper.insert(like);
            comment.setLikeCount(comment.getLikeCount() + 1);
            commentMapper.updateById(comment);
            return R.ok(true);
        }
    }

    @Override
    public R<Void> deleteComment(Long userId, Long commentId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            return R.fail(404, "Comment not found");
        }
        if (!comment.getUserId().equals(userId)) {
            return R.fail(403, "Cannot delete other user's comment");
        }
        commentMapper.deleteById(commentId);
        return R.ok();
    }

    // ==================== Admin ====================

    @Override
    public R<PageResult<Map<String, Object>>> adminListComments(Integer page, Integer size, String keyword) {
        Page<Comment> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<Comment>()
                .orderByDesc(Comment::getCreateTime);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Comment::getContent, keyword);
        }
        Page<Comment> result = commentMapper.selectPage(pageParam, wrapper);
        List<Map<String, Object>> vos = new ArrayList<>();
        for (Comment comment : result.getRecords()) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", comment.getId());
            map.put("postId", comment.getPostId());
            map.put("userId", comment.getUserId());
            map.put("content", comment.getContent());
            map.put("likeCount", comment.getLikeCount());
            map.put("createTime", comment.getCreateTime());
            UserVO author = userCacheService.getUserById(comment.getUserId());
            map.put("author", author);
            vos.add(map);
        }
        return R.ok(new PageResult<>(vos, result.getTotal(), page, size));
    }

    @Override
    public R<Void> adminDeleteComment(Long commentId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            return R.fail(404, "Comment not found");
        }
        commentMapper.deleteById(commentId);
        return R.ok();
    }

    @Override
    public R<Map<String, Object>> adminGetCommentStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalComments", commentMapper.selectCount(null));
        stats.put("todayNewComments", commentMapper.selectCount(
                new LambdaQueryWrapper<Comment>()
                        .ge(Comment::getCreateTime, java.time.LocalDate.now().atStartOfDay())));
        stats.put("totalCommentLikes", commentLikeMapper.selectCount(null));
        return R.ok(stats);
    }

    private CommentVO toVO(Comment comment, Long currentUserId) {
        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setContent(comment.getContent());
        vo.setLikeCount(comment.getLikeCount());
        vo.setParentId(comment.getParentId());
        vo.setCreateTime(comment.getCreateTime());

        vo.setAuthor(userCacheService.getUserById(comment.getUserId()));

        if (currentUserId != null) {
            Long count = commentLikeMapper.selectCount(
                    new LambdaQueryWrapper<CommentLike>()
                            .eq(CommentLike::getCommentId, comment.getId())
                            .eq(CommentLike::getUserId, currentUserId));
            vo.setLiked(count > 0);
        }
        return vo;
    }
}

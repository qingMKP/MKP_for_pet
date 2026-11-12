package com.mkp.post.controller;

import com.mkp.common.dto.CommentSaveDTO;
import com.mkp.common.result.R;
import com.mkp.post.service.CommentService;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    private Long getUserId(HttpServletRequest request) {
        String header = request.getHeader("X-User-Id");
        return header != null ? Long.parseLong(header) : null;
    }

    @GetMapping("/api/post/{postId}/comments")
    public R<?> listComments(@PathVariable Long postId, HttpServletRequest request) {
        return commentService.listComments(postId, getUserId(request));
    }

    @PostMapping("/api/post/{postId}/comments")
    public R<?> createComment(@PathVariable Long postId,
                              @RequestBody CommentSaveDTO dto,
                              HttpServletRequest request) {
        return commentService.createComment(getUserId(request), postId, dto);
    }

    @PostMapping("/api/post/comment/{commentId}/like")
    public R<Boolean> toggleLike(@PathVariable Long commentId, HttpServletRequest request) {
        return commentService.toggleLike(getUserId(request), commentId);
    }

    @DeleteMapping("/api/post/comment/{commentId}")
    public R<Void> deleteComment(@PathVariable Long commentId, HttpServletRequest request) {
        return commentService.deleteComment(getUserId(request), commentId);
    }
}

package com.mkp.post.controller;

import com.mkp.common.dto.PageResult;
import com.mkp.common.result.R;
import com.mkp.post.service.CommentService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/comment")
public class AdminCommentController {

    private final CommentService commentService;

    public AdminCommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping("/list")
    public R<PageResult<Map<String, Object>>> listComments(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword) {
        return commentService.adminListComments(page, size, keyword);
    }

    @DeleteMapping("/{id}")
    public R<Void> deleteComment(@PathVariable Long id) {
        return commentService.adminDeleteComment(id);
    }

    @GetMapping("/stats")
    public R<?> getCommentStats() {
        return commentService.adminGetCommentStats();
    }
}

package com.mkp.post.controller;

import com.mkp.common.dto.PageResult;
import com.mkp.common.dto.PostVO;
import com.mkp.common.result.R;
import com.mkp.post.service.PostService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/post")
public class AdminPostController {

    private final PostService postService;

    public AdminPostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping("/list")
    public R<PageResult<PostVO>> listPosts(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword) {
        return postService.adminListPosts(page, size, keyword);
    }

    @DeleteMapping("/{id}")
    public R<Void> deletePost(@PathVariable Long id) {
        return postService.adminDeletePost(id);
    }

    /** 刷新某个作者全部帖子的索引（用户改昵称后可手动补一次） */
    @PostMapping("/search/refresh-author")
    public R<Long> refreshAuthorIndex(@RequestParam("userId") Long userId) {
        return postService.refreshAuthorIndex(userId);
    }

    /** 把库里所有上架帖子重刷进 ES（换分词器、索引丢失后用它补数据） */
    @PostMapping("/search/rebuild")
    public R<Long> rebuildSearchIndex() {
        return postService.rebuildSearchIndex();
    }

    /** 查看搜索索引状态（ES 是否可用、文档数） */
    @GetMapping("/search/status")
    public R<String> getSearchStatus() {
        return postService.getSearchStatus();
    }

    @GetMapping("/stats")
    public R<?> getPostStats() {
        return postService.adminGetPostStats();
    }

    @GetMapping("/chart/daily")
    public R<?> getDailyTrend() {
        return postService.adminGetDailyTrend();
    }
}

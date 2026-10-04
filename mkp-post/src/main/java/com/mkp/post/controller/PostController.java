package com.mkp.post.controller;

import com.mkp.common.dto.PostSaveDTO;
import com.mkp.common.result.R;
import com.mkp.post.service.OssService;
import com.mkp.post.service.PostService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/post")
public class PostController {

    private final PostService postService;
    private final OssService ossService;

    public PostController(PostService postService, OssService ossService) {
        this.postService = postService;
        this.ossService = ossService;
    }

    private Long getUserId(HttpServletRequest request) {
        String header = request.getHeader("X-User-Id");
        return header != null ? Long.parseLong(header) : null;
    }

    @GetMapping("/list")
    public R<?> listPosts(@RequestParam(defaultValue = "1") Integer page,
                          @RequestParam(defaultValue = "10") Integer size,
                          HttpServletRequest request) {
        return postService.listPosts(page, size, getUserId(request));
    }

    /**
     * 社区广场搜索：keyword 同时匹配帖子文案和作者昵称。
     * sort: relevance（相关度，默认）/ latest（最新）/ hot（最热）
     */
    @GetMapping("/search")
    public R<?> searchPosts(@RequestParam(value = "keyword", required = false, defaultValue = "") String keyword,
                            @RequestParam(defaultValue = "1") Integer page,
                            @RequestParam(defaultValue = "10") Integer size,
                            @RequestParam(defaultValue = "relevance") String sort,
                            @RequestParam(required = false) Boolean hasImage,
                            @RequestParam(required = false) String topic,
                            HttpServletRequest request) {
        return postService.searchPosts(keyword, page, size, sort, hasImage, topic, getUserId(request));
    }

    @GetMapping("/{id}")
    public R<?> getPostDetail(@PathVariable Long id, HttpServletRequest request) {
        return postService.getPostDetail(id, getUserId(request));
    }

    @PostMapping
    public R<?> createPost(@RequestBody PostSaveDTO dto, HttpServletRequest request) {
        return postService.createPost(getUserId(request), dto);
    }

    @DeleteMapping("/{id}")
    public R<Void> deletePost(@PathVariable Long id, HttpServletRequest request) {
        return postService.deletePost(getUserId(request), id);
    }

    @PostMapping("/{id}/like")
    public R<Boolean> toggleLike(@PathVariable Long id, HttpServletRequest request) {
        return postService.toggleLike(getUserId(request), id);
    }

    @PostMapping("/image/upload")
    public R<List<String>> uploadImages(@RequestParam("files") MultipartFile[] files,
                                         HttpServletRequest request) {
        Long userId = getUserId(request);
        List<String> urls = ossService.uploadImages(files, userId);
        return R.ok(urls);
    }
}

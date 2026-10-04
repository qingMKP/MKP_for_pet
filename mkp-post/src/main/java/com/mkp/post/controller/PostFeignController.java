package com.mkp.post.controller;

import com.mkp.common.dto.ChatbotPostVO;
import com.mkp.common.result.R;
import com.mkp.post.service.PostService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/post/feign")
public class PostFeignController {

    private final PostService postService;

    public PostFeignController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping("/search")
    public R<List<ChatbotPostVO>> searchPosts(@RequestParam("keyword") String keyword) {
        return postService.searchPosts(keyword);
    }
}

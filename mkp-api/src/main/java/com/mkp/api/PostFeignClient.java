package com.mkp.api;

import com.mkp.api.fallback.PostFeignClientFallback;
import com.mkp.common.dto.ChatbotPostVO;
import com.mkp.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "mkp-post", path = "/api/post", fallback = PostFeignClientFallback.class)
public interface PostFeignClient {

    @GetMapping("/feign/search")
    R<List<ChatbotPostVO>> searchPosts(@RequestParam("keyword") String keyword);
}

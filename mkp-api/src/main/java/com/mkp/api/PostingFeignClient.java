package com.mkp.api;

import com.mkp.api.fallback.PostingFeignClientFallback;
import com.mkp.common.dto.PostingVO;
import com.mkp.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "mkp-pet", path = "/api/posting", fallback = PostingFeignClientFallback.class)
public interface PostingFeignClient {

    @GetMapping("/list")
    R<List<PostingVO>> listOpen(@RequestParam("category") Integer category);
}

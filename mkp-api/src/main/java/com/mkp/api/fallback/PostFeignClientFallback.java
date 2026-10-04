package com.mkp.api.fallback;

import com.mkp.api.PostFeignClient;
import com.mkp.common.dto.ChatbotPostVO;
import com.mkp.common.result.R;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PostFeignClientFallback implements PostFeignClient {

    @Override
    public R<List<ChatbotPostVO>> searchPosts(String keyword) {
        return R.ok(List.of());
    }
}

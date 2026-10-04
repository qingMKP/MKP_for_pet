package com.mkp.api.fallback;

import com.mkp.api.PostingFeignClient;
import com.mkp.common.dto.PostingVO;
import com.mkp.common.result.R;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PostingFeignClientFallback implements PostingFeignClient {

    @Override
    public R<List<PostingVO>> listOpen(Integer category) {
        return R.ok(List.of());
    }
}

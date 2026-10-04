package com.mkp.api;

import com.mkp.api.fallback.UserFeignClientFallback;
import com.mkp.common.dto.UserVO;
import com.mkp.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "mkp-user", path = "/api/user", fallback = UserFeignClientFallback.class)
public interface UserFeignClient {

    @GetMapping("/feign/{id}")
    R<UserVO> getById(@PathVariable("id") Long id);

    @GetMapping("/feign/friend/check")
    R<Boolean> areFriends(@RequestParam("user1") Long user1,
                          @RequestParam("user2") Long user2);

    @PostMapping("/feign/batch")
    R<Map<Long, UserVO>> getByIds(@RequestBody List<Long> ids);

    /** 按昵称 / 用户名模糊匹配用户 id（帖子搜索降级时用来兜住「按作者名字搜」） */
    @GetMapping("/feign/search-ids")
    R<List<Long>> searchUserIds(@RequestParam("keyword") String keyword,
                                @RequestParam(value = "limit", defaultValue = "200") Integer limit);
}

package com.mkp.user.controller;

import com.mkp.common.dto.UserVO;
import com.mkp.common.result.R;
import com.mkp.user.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user/feign")
public class UserFeignController {

    private final UserService userService;

    public UserFeignController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public R<UserVO> getById(@PathVariable Long id) {
        return userService.getById(id);
    }

    @GetMapping("/friend/check")
    public R<Boolean> areFriends(@RequestParam Long user1, @RequestParam Long user2) {
        return userService.areFriends(user1, user2);
    }

    @PostMapping("/batch")
    public R<Map<Long,UserVO>> getByIds(@RequestBody List<Long> ids){
        return userService.getByIds(ids);
    }

    @GetMapping("/search-ids")
    public R<List<Long>> searchUserIds(@RequestParam("keyword") String keyword,
                                       @RequestParam(value = "limit", defaultValue = "200") Integer limit) {
        return userService.searchUserIds(keyword, limit);
    }
}

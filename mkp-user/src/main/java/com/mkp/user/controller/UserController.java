package com.mkp.user.controller;

import com.mkp.common.dto.UpdateProfileDTO;
import com.mkp.common.result.R;
import com.mkp.user.service.OssService;
import com.mkp.user.service.UserService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final OssService ossService;

    public UserController(UserService userService, OssService ossService) {
        this.userService = userService;
        this.ossService = ossService;
    }

    private Long getUserId(HttpServletRequest request) {
        return Long.parseLong(request.getHeader("X-User-Id"));
    }

    @GetMapping("/profile")
    public R<?> getProfile(HttpServletRequest request) {
        return userService.getProfile(getUserId(request));
    }

    @PutMapping("/profile")
    public R<Void> updateProfile(@RequestBody UpdateProfileDTO dto, HttpServletRequest request) {
        return userService.updateProfile(getUserId(request), dto);
    }

    @GetMapping("/friend/list")
    public R<?> getFriendList(HttpServletRequest request) {
        return userService.getFriendList(getUserId(request));
    }

    @PostMapping("/friend/add/{friendId}")
    public R<Void> addFriend(@PathVariable Long friendId, HttpServletRequest request) {
        return userService.addFriend(getUserId(request), friendId);
    }

    @PostMapping("/friend/accept/{friendId}")
    public R<Void> acceptFriend(@PathVariable Long friendId, HttpServletRequest request) {
        return userService.acceptFriend(getUserId(request), friendId);
    }

    @PostMapping("/friend/reject/{friendId}")
    public R<Void> rejectFriend(@PathVariable Long friendId, HttpServletRequest request) {
        return userService.rejectFriend(getUserId(request), friendId);
    }

    @PostMapping("/avatar/upload")
    public R<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file,
                                               HttpServletRequest request) {
        Long userId = getUserId(request);
        String url = ossService.uploadAvatar(file, userId);
        userService.updateAvatar(userId, url);
        return R.ok(Map.of("url", url));
    }
}

package com.mkp.user.controller;

import com.mkp.common.dto.PageResult;
import com.mkp.common.dto.UserVO;
import com.mkp.common.result.R;
import com.mkp.user.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/user")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/list")
    public R<PageResult<UserVO>> listUsers(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword) {
        return userService.adminListUsers(page, size, keyword);
    }

    @PutMapping("/{id}/status")
    public R<Void> toggleStatus(@PathVariable Long id, @RequestParam Integer status) {
        return userService.adminToggleStatus(id, status);
    }

    @PutMapping("/{id}/role")
    public R<Void> updateRole(@PathVariable Long id, @RequestParam String role) {
        return userService.adminUpdateRole(id, role);
    }

    @PutMapping("/{id}/reset-password")
    public R<Void> resetPassword(@PathVariable Long id) {
        return userService.adminResetPassword(id);
    }

    @GetMapping("/stats")
    public R<Map<String, Object>> getStats() {
        return userService.adminGetStats();
    }
}

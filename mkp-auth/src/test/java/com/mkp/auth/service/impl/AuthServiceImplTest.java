package com.mkp.auth.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mkp.auth.mapper.UserMapper;
import com.mkp.common.constant.RoleConstants;
import com.mkp.common.entity.User;
import com.mkp.common.result.R;
import com.mkp.common.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AuthServiceImpl authService;

    // ==================== Login Tests ====================

    @Test
    void login_success() {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPassword(BCrypt.hashpw("123456"));
        user.setRole(RoleConstants.USER);
        user.setStatus(1);

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        R<Map<String, Object>> result = authService.login("testuser", "123456");

        assertEquals(200, result.getCode());
        assertNotNull(result.getData().get("token"));
        assertEquals(1L, result.getData().get("userId"));
        assertEquals("testuser", result.getData().get("username"));
    }

    @Test
    void login_userNotFound() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        R<Map<String, Object>> result = authService.login("nouser", "123456");

        assertEquals(401, result.getCode());
        assertNull(result.getData());
    }

    @Test
    void login_wrongPassword() {
        User user = new User();
        user.setId(1L);
        user.setPassword(BCrypt.hashpw("123456"));

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        R<Map<String, Object>> result = authService.login("testuser", "wrongpwd");

        assertEquals(401, result.getCode());
    }

    @Test
    void login_accountDisabled() {
        User user = new User();
        user.setId(1L);
        user.setPassword(BCrypt.hashpw("123456"));
        user.setStatus(0);

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        R<Map<String, Object>> result = authService.login("testuser", "123456");

        assertEquals(403, result.getCode());
    }

    // ==================== Register Tests ====================

    @Test
    void register_success() {
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(userMapper.insert(any(User.class))).thenReturn(1);

        R<Void> result = authService.register("newuser", "123456", "Nick");

        assertEquals(200, result.getCode());
        verify(userMapper).insert(argThat((User user) ->
                "newuser".equals(user.getUsername()) &&
                "Nick".equals(user.getNickname()) &&
                RoleConstants.USER.equals(user.getRole()) &&
                user.getStatus() == 1
        ));
    }

    @Test
    void register_usernameExists() {
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        R<Void> result = authService.register("existinguser", "123456", "Nick");

        assertEquals(500, result.getCode());
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void register_nicknameDefaultToUsername() {
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(userMapper.insert(any(User.class))).thenReturn(1);

        authService.register("newuser", "123456", null);

        verify(userMapper).insert(argThat((User user) ->
                "newuser".equals(user.getNickname())
        ));
    }
}

package com.mkp.auth.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mkp.auth.mapper.UserMapper;
import com.mkp.auth.service.AuthService;
import com.mkp.common.entity.User;
import com.mkp.common.constant.RoleConstants;
import com.mkp.common.result.R;
import com.mkp.common.util.JwtUtil;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;

    public AuthServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public R<Map<String, Object>> login(String username, String password) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        );
        if (user == null || !BCrypt.checkpw(password, user.getPassword())) {
            return R.fail(401, "Incorrect username or password");
        }
        if (user.getStatus() == 0) {
            return R.fail(403, "Account has been disabled");
        }
        String token = JwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("nickname", user.getNickname());
        result.put("avatar", user.getAvatar());
        result.put("role", user.getRole());
        return R.ok(result);
    }

    @Override
    public R<Void> register(String username, String password, String nickname) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        );
        if (count > 0) {
            return R.fail("Username already exists");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(BCrypt.hashpw(password));
        user.setNickname(nickname != null ? nickname : username);
        user.setRole(RoleConstants.USER);
        user.setStatus(1);
        userMapper.insert(user);
        return R.ok();
    }
}

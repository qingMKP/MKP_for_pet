package com.mkp.auth.controller;

import com.mkp.auth.service.AuthService;
import com.mkp.common.dto.LoginDTO;
import com.mkp.common.dto.RegisterDTO;
import com.mkp.common.result.R;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody LoginDTO dto) {
        return authService.login(dto.getUsername(), dto.getPassword());
    }

    @PostMapping("/register")
    public R<Void> register(@RequestBody RegisterDTO dto) {
        return authService.register(dto.getUsername(), dto.getPassword(), dto.getNickname());
    }
}

package com.mkp.auth.service;

import com.mkp.common.result.R;

import java.util.Map;

public interface AuthService {
    R<Map<String, Object>> login(String username, String password);
    R<Void> register(String username, String password, String nickname);
}

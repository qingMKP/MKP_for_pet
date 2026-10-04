package com.mkp.chatbot.service.react;

/**
 * 工具未找到异常
 */
public class ToolNotFoundException extends RuntimeException {

    public ToolNotFoundException(String message) {
        super(message);
    }
}

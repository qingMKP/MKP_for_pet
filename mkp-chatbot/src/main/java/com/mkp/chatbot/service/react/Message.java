package com.mkp.chatbot.service.react;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ReAct 引擎内部使用的消息结构
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    private String role;
    private String content;
}

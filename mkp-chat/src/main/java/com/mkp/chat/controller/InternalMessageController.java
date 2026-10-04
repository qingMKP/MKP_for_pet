package com.mkp.chat.controller;

import cn.hutool.core.util.StrUtil;
import com.mkp.chat.service.ChatService;
import com.mkp.common.dto.MessageVO;
import com.mkp.common.result.R;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 服务间内部接口（发系统通知私信）。
 *
 * 路径故意**不带 /api 前缀**：mkp-gateway 只路由 /api/**，所以这个接口不会暴露给前端/公网；
 * 只有集群内部的服务（如 mkp-pet 的定时任务）能直连调用。再用一个内部令牌做最小防护。
 */
@RestController
@RequestMapping("/internal/message")
public class InternalMessageController {

    private final ChatService chatService;

    @Value("${mkp.internal.token:}")
    private String internalToken;

    public InternalMessageController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** body: { "toUserId": 5, "content": "..." } */
    @PostMapping("/system")
    public R<MessageVO> sendSystem(@RequestBody Map<String, Object> body,
                                   @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        if (StrUtil.isNotBlank(internalToken) && !internalToken.equals(token)) {
            return R.fail(403, "非法调用");
        }
        Object to = body.get("toUserId");
        Object content = body.get("content");
        if (to == null || content == null) {
            return R.fail("toUserId 与 content 不能为空");
        }
        return chatService.sendSystemMessage(Long.valueOf(String.valueOf(to)), String.valueOf(content));
    }
}

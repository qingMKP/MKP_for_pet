package com.mkp.chat.controller;

import com.mkp.chat.service.ChatService;
import com.mkp.common.result.R;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/message")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    private Long getUserId(HttpServletRequest request) {
        System.out.println("X-User-Id:"+request.getHeader("X-User-Id"));
        return Long.parseLong(request.getHeader("X-User-Id"));
    }

    @PostMapping("/send")
    public R<?> sendMessage(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long toUserId = Long.valueOf(body.get("toUserId").toString());
        String content = (String) body.get("content");
        return chatService.sendMessage(getUserId(request), toUserId, content);
    }

    @GetMapping("/conversation/{friendId}")
    public R<?> getConversation(@PathVariable Long friendId, HttpServletRequest request) {
        return chatService.getConversation(getUserId(request), friendId);
    }

    @GetMapping("/conversations")
    public R<?> getConversations(HttpServletRequest request) {
        return chatService.getConversations(getUserId(request));
    }

    @GetMapping("/unread")
    public R<Integer> getUnreadCount(HttpServletRequest request) {
        return chatService.getUnreadCount(getUserId(request));
    }

    @PutMapping("/read/{friendId}")
    public R<Void> markAsRead(@PathVariable Long friendId, HttpServletRequest request) {
        return chatService.markAsRead(getUserId(request), friendId);
    }
}

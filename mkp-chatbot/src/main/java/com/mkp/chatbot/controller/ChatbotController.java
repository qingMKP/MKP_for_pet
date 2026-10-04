package com.mkp.chatbot.controller;

import com.mkp.chatbot.service.ChatbotService;
import com.mkp.common.dto.ChatbotAskDTO;
import com.mkp.common.dto.ChatbotReplyVO;
import com.mkp.common.dto.KnowledgeSaveDTO;
import com.mkp.common.entity.ChatbotConversation;
import com.mkp.common.entity.ChatbotKnowledge;
import com.mkp.common.entity.ChatbotMessage;
import com.mkp.common.result.R;
import com.mkp.common.util.UserContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {

    private final ChatbotService chatbotService;

    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    // ==================== 同步问答（保留作为降级兜底） ====================

    @PostMapping("/ask")
    public R<ChatbotReplyVO> ask(@RequestBody ChatbotAskDTO dto) {
        return chatbotService.ask(dto.getQuestion(), dto.getConversationId());
    }

    // ==================== 流式问答（SSE） ====================

    @GetMapping("/ask/stream")
    public SseEmitter askStream(
            @RequestParam String question,
            @RequestParam(required = false) Long conversationId) {
        SseEmitter emitter = new SseEmitter(120_000L);
        // 在异步前捕获请求线程的 UserContext（ThreadLocal 不跨线程）
        Long userId = UserContext.getUserId();
        String username = UserContext.getUsername();
        String role = UserContext.getRole();
        CompletableFuture.runAsync(() -> {
            UserContext.setUserId(userId);
            UserContext.setUsername(username);
            UserContext.setRole(role);
            try {
                chatbotService.askStream(question, conversationId, emitter);
            } finally {
                UserContext.clear();
            }
        });
        return emitter;
    }

    // ==================== 会话管理 ====================

    @GetMapping("/conversations")
    public R<List<ChatbotConversation>> listConversations() {
        return chatbotService.listConversations();
    }

    @GetMapping("/conversation/{id}/messages")
    public R<List<ChatbotMessage>> getConversationMessages(@PathVariable Long id) {
        return chatbotService.getConversationMessages(id);
    }

    @DeleteMapping("/conversation/{id}")
    public R<Void> deleteConversation(@PathVariable Long id) {
        return chatbotService.deleteConversation(id);
    }

    // ==================== 工具管理 ====================

    @GetMapping("/tools")
    public R<List<String>> getAvailableTools() {
        return R.ok(chatbotService.getAvailableTools());
    }

    // ==================== 知识库管理 ====================

    @GetMapping("/knowledge/list")
    public R<List<ChatbotKnowledge>> listKnowledge() {
        return chatbotService.listKnowledge();
    }

    @PostMapping("/knowledge")
    public R<Void> addKnowledge(@RequestBody KnowledgeSaveDTO dto) {
        return chatbotService.addKnowledge(dto);
    }

    @PutMapping("/knowledge/{id}")
    public R<Void> updateKnowledge(@PathVariable Long id, @RequestBody KnowledgeSaveDTO dto) {
        return chatbotService.updateKnowledge(id, dto);
    }

    @DeleteMapping("/knowledge/{id}")
    public R<Void> deleteKnowledge(@PathVariable Long id) {
        return chatbotService.deleteKnowledge(id);
    }

    @DeleteMapping("/knowledge/batch")
    public R<Void> batchDeleteKnowledge(@RequestBody List<Long> ids) {
        return chatbotService.batchDeleteKnowledge(ids);
    }
}

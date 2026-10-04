package com.mkp.chatbot.service;

import com.mkp.common.dto.ChatbotReplyVO;
import com.mkp.common.dto.KnowledgeSaveDTO;
import com.mkp.common.entity.ChatbotConversation;
import com.mkp.common.entity.ChatbotKnowledge;
import com.mkp.common.entity.ChatbotMessage;
import com.mkp.common.result.R;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface ChatbotService {
    R<ChatbotReplyVO> ask(String question, Long conversationId);

    void askStream(String question, Long conversationId, SseEmitter emitter);

    R<List<ChatbotConversation>> listConversations();

    R<List<ChatbotMessage>> getConversationMessages(Long conversationId);

    R<Void> deleteConversation(Long conversationId);

    R<List<ChatbotKnowledge>> listKnowledge();

    R<Void> addKnowledge(KnowledgeSaveDTO dto);

    R<Void> updateKnowledge(Long id, KnowledgeSaveDTO dto);

    R<Void> deleteKnowledge(Long id);

    R<Void> batchDeleteKnowledge(List<Long> ids);

    /**
     * 获取当前已注册的所有工具名称
     */
    List<String> getAvailableTools();
}

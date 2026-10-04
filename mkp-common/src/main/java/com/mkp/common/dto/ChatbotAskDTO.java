package com.mkp.common.dto;

public class ChatbotAskDTO {
    private String question;
    private Long conversationId;

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
}

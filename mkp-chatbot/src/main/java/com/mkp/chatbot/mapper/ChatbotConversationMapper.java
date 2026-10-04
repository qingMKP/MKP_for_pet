package com.mkp.chatbot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkp.common.entity.ChatbotConversation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatbotConversationMapper extends BaseMapper<ChatbotConversation> {
}

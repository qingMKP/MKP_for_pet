package com.mkp.chatbot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkp.common.entity.ChatbotMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatbotMessageMapper extends BaseMapper<ChatbotMessage> {
}

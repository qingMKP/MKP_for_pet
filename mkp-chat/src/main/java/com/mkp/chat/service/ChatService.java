package com.mkp.chat.service;

import com.mkp.common.dto.FriendVO;
import com.mkp.common.dto.MessageVO;
import com.mkp.common.result.R;

import java.util.List;

public interface ChatService {
    R<MessageVO> sendMessage(Long fromUserId, Long toUserId, String content);
    /** 系统通知（站内私信）：发送方固定为"MKP 官方"，不校验好友关系，只允许服务间内部调用 */
    R<MessageVO> sendSystemMessage(Long toUserId, String content);
    R<List<MessageVO>> getConversation(Long userId, Long friendId);
    R<List<FriendVO>> getConversations(Long userId);
    R<Integer> getUnreadCount(Long userId);
    R<Void> markAsRead(Long userId, Long friendId);
}

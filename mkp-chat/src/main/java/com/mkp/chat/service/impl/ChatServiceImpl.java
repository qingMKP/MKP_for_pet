package com.mkp.chat.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mkp.chat.mapper.FriendRelationMapper;
import com.mkp.chat.mapper.MessageMapper;
import com.mkp.chat.service.ChatService;
import com.mkp.chat.utils.UserCacheService;
import com.mkp.common.constant.NotifyConstants;
import com.mkp.common.dto.FriendVO;
import com.mkp.common.dto.MessageVO;
import com.mkp.common.dto.UserVO;
import com.mkp.common.entity.FriendRelation;
import com.mkp.common.entity.Message;
import com.mkp.common.result.R;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ChatServiceImpl implements ChatService {

    private final MessageMapper messageMapper;
    private final FriendRelationMapper friendRelationMapper;
    private final UserCacheService userCacheService;

    public ChatServiceImpl(MessageMapper messageMapper,
                           FriendRelationMapper friendRelationMapper,
                           UserCacheService userCacheService) {
        this.messageMapper = messageMapper;
        this.friendRelationMapper = friendRelationMapper;
        this.userCacheService = userCacheService;
    }

    @Override
    public R<MessageVO> sendMessage(Long fromUserId, Long toUserId, String content) {
        if (StrUtil.isBlank(content)) {
            return R.fail("Message cannot be empty");
        }
        // 系统通知不支持回复：发件人是"MKP 官方"（from_user_id = 0），没有真实用户可接收
        if (toUserId == null || toUserId == NotifyConstants.SYSTEM_USER_ID) {
            return R.fail("系统通知不支持回复");
        }
        // Verify friendship
        Long isFriend = friendRelationMapper.selectCount(new LambdaQueryWrapper<FriendRelation>()
                .eq(FriendRelation::getUserId, Math.min(fromUserId, toUserId))
                .eq(FriendRelation::getFriendId, Math.max(fromUserId, toUserId))
                .eq(FriendRelation::getStatus, 1));
        if (isFriend == 0) {
            return R.fail("You are not friends with this user");
        }
        Message message = new Message();
        message.setFromUserId(fromUserId);
        message.setToUserId(toUserId);
        message.setContent(content);
        message.setMessageType(0);
        message.setIsRead(0);
        messageMapper.insert(message);
        return R.ok(toVO(message));
    }

    @Override
    public R<MessageVO> sendSystemMessage(Long toUserId, String content) {
        if (toUserId == null || toUserId <= 0) {
            return R.fail("toUserId 不合法");
        }
        if (StrUtil.isBlank(content)) {
            return R.fail("Message cannot be empty");
        }
        Message message = new Message();
        message.setFromUserId(NotifyConstants.SYSTEM_USER_ID);
        message.setToUserId(toUserId);
        message.setContent(content);
        message.setMessageType(NotifyConstants.MESSAGE_TYPE_SYSTEM);
        message.setIsRead(0);
        messageMapper.insert(message);
        return R.ok(toVO(message));
    }

    @Override
    public R<List<MessageVO>> getConversation(Long userId, Long friendId) {
        // Mark messages from this friend as read
        markAsRead(userId, friendId);

        List<Message> messages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .and(w -> w.eq(Message::getFromUserId, userId)
                                .eq(Message::getToUserId, friendId))
                        .or(w -> w.eq(Message::getFromUserId, friendId)
                                .eq(Message::getToUserId, userId))
                        .orderByAsc(Message::getCreateTime));
        List<MessageVO> vos = new ArrayList<>();
        for (Message msg : messages) {
            vos.add(toVO(msg));
        }
        return R.ok(vos);
    }

    @Override
    public R<List<FriendVO>> getConversations(Long userId) {
        Set<Long> friendIds = new LinkedHashSet<>();

        // Users with message history
        List<Message> toMessages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getFromUserId, userId)
                        .select(Message::getToUserId)
                        .groupBy(Message::getToUserId));
        List<Message> fromMessages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getToUserId, userId)
                        .select(Message::getFromUserId)
                        .groupBy(Message::getFromUserId));
        for (Message m : toMessages) friendIds.add(m.getToUserId());
        for (Message m : fromMessages) friendIds.add(m.getFromUserId());

        // Confirmed friends from friend_relation (even without message history)
        List<FriendRelation> relations = friendRelationMapper.selectList(
                new LambdaQueryWrapper<FriendRelation>()
                        .eq(FriendRelation::getStatus, 1)
                        .and(w -> w.eq(FriendRelation::getUserId, userId)
                                .or().eq(FriendRelation::getFriendId, userId)));
        for (FriendRelation rel : relations) {
            Long friendId = rel.getUserId().equals(userId) ? rel.getFriendId() : rel.getUserId();
            friendIds.add(friendId);
        }

        List<FriendVO> vos = new ArrayList<>();
        // 系统通知会话（from_user_id = 0）在 mkp_user 里查不到，必须单独补一条，
        // 否则下面的用户查询会把这条私信从会话列表里过滤掉，用户永远看不到。
        Long systemTotal = messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getFromUserId, NotifyConstants.SYSTEM_USER_ID)
                .eq(Message::getToUserId, userId));
        if (systemTotal != null && systemTotal > 0) {
            Long systemUnread = messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                    .eq(Message::getFromUserId, NotifyConstants.SYSTEM_USER_ID)
                    .eq(Message::getToUserId, userId)
                    .eq(Message::getIsRead, 0));
            FriendVO sys = new FriendVO();
            sys.setId(NotifyConstants.SYSTEM_USER_ID);
            sys.setNickname("MKP 官方");
            sys.setUnreadCount(systemUnread == null ? 0 : systemUnread.intValue());
            vos.add(sys);
        }
        for (Long friendId : friendIds) {
            try {
                UserVO user = userCacheService.getUserById(friendId);
                if (user != null) {
                    FriendVO vo = new FriendVO();
                    vo.setId(user.getId());
                    vo.setNickname(user.getNickname());
                    vo.setAvatar(user.getAvatar());
                    Long unread = messageMapper.selectCount(
                            new LambdaQueryWrapper<Message>()
                                    .eq(Message::getFromUserId, friendId)
                                    .eq(Message::getToUserId, userId)
                                    .eq(Message::getIsRead, 0));
                    vo.setUnreadCount(unread.intValue());
                    vos.add(vo);
                }
            } catch (Exception ignored) {}
        }
        return R.ok(vos);
    }

    @Override
    public R<Integer> getUnreadCount(Long userId) {
        Long count = messageMapper.selectCount(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getToUserId, userId)
                        .eq(Message::getIsRead, 0));
        return R.ok(count.intValue());
    }

    @Override
    public R<Void> markAsRead(Long userId, Long friendId) {
        List<Message> unread = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getFromUserId, friendId)
                        .eq(Message::getToUserId, userId)
                        .eq(Message::getIsRead, 0));
        for (Message msg : unread) {
            msg.setIsRead(1);
            messageMapper.updateById(msg);
        }
        return R.ok();
    }

    private MessageVO toVO(Message msg) {
        MessageVO vo = new MessageVO();
        vo.setId(msg.getId());
        vo.setFromUserId(msg.getFromUserId());
        vo.setToUserId(msg.getToUserId());
        vo.setContent(msg.getContent());
        vo.setMessageType(msg.getMessageType());
        vo.setIsRead(msg.getIsRead());
        vo.setCreateTime(msg.getCreateTime());
        return vo;
    }
}

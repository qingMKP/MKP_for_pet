package com.mkp.user.service;

import com.mkp.common.dto.FriendVO;
import com.mkp.common.dto.PageResult;
import com.mkp.common.dto.UpdateProfileDTO;
import com.mkp.common.dto.UserVO;
import com.mkp.common.result.R;

import java.util.List;
import java.util.Map;

public interface UserService {
    R<UserVO> getProfile(Long userId);
    R<Void> updateProfile(Long userId, UpdateProfileDTO dto);
    R<List<FriendVO>> getFriendList(Long userId);
    R<Void> addFriend(Long userId, Long friendId);
    R<Void> acceptFriend(Long userId, Long friendUserId);
    R<Void> rejectFriend(Long userId, Long friendUserId);
    R<UserVO> getById(Long id);
    R<Boolean> areFriends(Long user1, Long user2);
    R<String> updateAvatar(Long userId, String avatarUrl);
    R<Map<Long, UserVO>> getByIds(List<Long> ids);
    R<List<Long>> searchUserIds(String keyword, Integer limit);

    // Admin
    R<PageResult<UserVO>> adminListUsers(Integer page, Integer size, String keyword);
    R<Void> adminToggleStatus(Long userId, Integer status);
    R<Void> adminUpdateRole(Long userId, String role);
    R<Void> adminResetPassword(Long userId);
    R<Map<String, Object>> adminGetStats();
}

package com.mkp.user.service.impl;

import cn.hutool.json.JSONUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mkp.common.config.RedisConfig;
import com.mkp.common.constant.RedisKey;
import com.mkp.common.dto.FriendVO;
import com.mkp.common.dto.PageResult;
import com.mkp.common.dto.UpdateProfileDTO;
import com.mkp.common.dto.UserVO;
import com.mkp.common.entity.FriendRelation;
import com.mkp.common.entity.User;
import com.mkp.common.result.R;
import com.mkp.user.mapper.FriendRelationMapper;
import com.mkp.user.mapper.UserMapper;
import com.mkp.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service

public class UserServiceImpl implements UserService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private final UserMapper userMapper;
    private final FriendRelationMapper friendRelationMapper;

    public UserServiceImpl(UserMapper userMapper, FriendRelationMapper friendRelationMapper) {
        this.userMapper = userMapper;
        this.friendRelationMapper = friendRelationMapper;
    }
    @Override
    public R<UserVO> getProfile(Long userId) {
        if (userId == null) {
            return R.fail(401, "Unauthorized");
        }
        String result = stringRedisTemplate.opsForValue().get("user:profile:" + userId);
        if (result != null) {
            User bean = JSONUtil.toBean(result, User.class);
            UserVO vo = toVO(bean);
            return R.ok(vo);
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return R.fail(404, "User not found");
        }
        String jsonStr = JSONUtil.toJsonStr(user);
        stringRedisTemplate.opsForValue().set("user:profile:"+userId, jsonStr,2, TimeUnit.HOURS);
        return R.ok(toVO(user));
    }

    @Override
    public R<Void> updateProfile(Long userId, UpdateProfileDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return R.fail(404, "User not found");
        }
        String oldNickname = user.getNickname();
        if (dto.getNickname() != null) user.setNickname(dto.getNickname());
        if (dto.getAvatar() != null) user.setAvatar(dto.getAvatar());
        if (dto.getPhone() != null) user.setPhone(dto.getPhone());
        userMapper.updateById(user);
        stringRedisTemplate.delete("user:profile:" + userId);
        // 昵称变了：清掉帖子服务缓存的作者信息，并把作者 id 记入待刷新集合，
        // 由 mkp-post 的定时任务重建这些帖子的 ES 索引（否则搜到的还是旧昵称）
        if (user.getNickname() != null && !user.getNickname().equals(oldNickname)) {
            stringRedisTemplate.delete("feign:user:" + userId);
            stringRedisTemplate.opsForSet().add(RedisKey.POST_AUTHOR_INDEX_DIRTY, String.valueOf(userId));
            stringRedisTemplate.expire(RedisKey.POST_AUTHOR_INDEX_DIRTY, 24, TimeUnit.HOURS);
        }
        return R.ok();
    }

    @Override
    public R<List<FriendVO>> getFriendList(Long userId) {
        List<FriendRelation> relations = friendRelationMapper.selectList(
                new LambdaQueryWrapper<FriendRelation>()
                        .in(FriendRelation::getStatus, 0, 1)
                        .and(w -> w.eq(FriendRelation::getUserId, userId)
                                .or().eq(FriendRelation::getFriendId, userId))
        );
        List<FriendVO> friends = new ArrayList<>();
        for (FriendRelation rel : relations) {
            // Only show pending requests where current user is the recipient
            if (rel.getStatus() == 0 && rel.getSenderId() != null && rel.getSenderId().equals(userId)) {
                continue;
            }
            Long friendId = rel.getUserId().equals(userId) ? rel.getFriendId() : rel.getUserId();
            User friend = userMapper.selectById(friendId);
            if (friend != null) {
                FriendVO vo = new FriendVO();
                vo.setId(friend.getId());
                vo.setNickname(friend.getNickname());
                vo.setAvatar(friend.getAvatar());
                vo.setStatus(rel.getStatus());
                friends.add(vo);
            }
        }
        return R.ok(friends);
    }

    @Override
    public R<Void> addFriend(Long userId, Long friendId) {
        if (userId.equals(friendId)) {
            return R.fail("Cannot add yourself as friend");
        }
        // Check if already exists
        Long exists = friendRelationMapper.selectCount(new LambdaQueryWrapper<FriendRelation>()
                .eq(FriendRelation::getUserId, Math.min(userId, friendId))
                .eq(FriendRelation::getFriendId, Math.max(userId, friendId))
        );
        if (exists > 0) {
            return R.fail("Friend request already exists");
        }
        FriendRelation rel = new FriendRelation();
        rel.setUserId(Math.min(userId, friendId));
        rel.setFriendId(Math.max(userId, friendId));
        rel.setSenderId(userId);
        rel.setStatus(0); // pending
        friendRelationMapper.insert(rel);
        return R.ok();
    }

    @Override
    public R<Void> acceptFriend(Long userId, Long friendUserId) {
        FriendRelation rel = friendRelationMapper.selectOne(new LambdaQueryWrapper<FriendRelation>()
                .eq(FriendRelation::getUserId, Math.min(userId, friendUserId))
                .eq(FriendRelation::getFriendId, Math.max(userId, friendUserId))
                .eq(FriendRelation::getStatus, 0)
        );
        if (rel == null) {
            return R.fail("Friend request not found");
        }
        if (rel.getSenderId() != null && rel.getSenderId().equals(userId)) {
            return R.fail("You cannot accept your own friend request");
        }
        rel.setStatus(1);
        friendRelationMapper.updateById(rel);
        return R.ok();
    }

    @Override
    public R<Void> rejectFriend(Long userId, Long friendUserId) {
        FriendRelation rel = friendRelationMapper.selectOne(new LambdaQueryWrapper<FriendRelation>()
                .eq(FriendRelation::getUserId, Math.min(userId, friendUserId))
                .eq(FriendRelation::getFriendId, Math.max(userId, friendUserId))
                .eq(FriendRelation::getStatus, 0)
        );
        if (rel == null) {
            return R.fail("Friend request not found");
        }
        if (rel.getSenderId() != null && rel.getSenderId().equals(userId)) {
            return R.fail("You cannot reject your own friend request");
        }
        rel.setStatus(2);
        friendRelationMapper.updateById(rel);
        return R.ok();
    }

    @Override
    public R<UserVO> getById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return R.fail(404, "User not found");
        }
        return R.ok(toVO(user));
    }

    @Override
    public R<String> updateAvatar(Long userId, String avatarUrl) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return R.fail(404, "User not found");
        }
        user.setAvatar(avatarUrl);
        userMapper.updateById(user);
        stringRedisTemplate.delete("user:profile:" + userId);
        return R.ok(avatarUrl);
    }

    @Override
    public R<Map<Long, UserVO>> getByIds(List<Long> ids) {
        List<User> users = userMapper.selectBatchIds(ids);
        if (users.isEmpty()){
            return R.fail(404, "Users not found");
        }
        Map<Long, UserVO> result = new HashMap<>();
        for (User user : users) {
            UserVO vo = toVO(user);
            result.put(user.getId(), vo);
        }
        return R.ok(result);
    }

    @Override
    public R<Boolean> areFriends(Long user1, Long user2) {
        Long count = friendRelationMapper.selectCount(new LambdaQueryWrapper<FriendRelation>()
                .eq(FriendRelation::getUserId, Math.min(user1, user2))
                .eq(FriendRelation::getFriendId, Math.max(user1, user2))
                .eq(FriendRelation::getStatus, 1)
        );
        return R.ok(count > 0);
    }

    /**
     * 按昵称 / 用户名模糊匹配用户 id，供帖子搜索的 MySQL 降级路径使用。
     * 只返回 id，数据量用 limit 兜住（默认 200，最多 500）。
     */
    @Override
    public R<List<Long>> searchUserIds(String keyword, Integer limit) {
        List<Long> ids = new ArrayList<>();
        if (keyword == null || keyword.isBlank()) {
            return R.ok(ids);
        }
        int size = (limit == null || limit <= 0 || limit > 500) ? 200 : limit;
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .select(User::getId)
                .and(w -> w.like(User::getNickname, keyword).or().like(User::getUsername, keyword))
                .last("LIMIT " + size));
        for (User user : users) {
            ids.add(user.getId());
        }
        return R.ok(ids);
    }

    // ==================== Admin ====================

    @Override
    public R<PageResult<UserVO>> adminListUsers(Integer page, Integer size, String keyword) {
        Page<User> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .orderByDesc(User::getCreateTime);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getNickname, keyword));
        }
        Page<User> result = userMapper.selectPage(pageParam, wrapper);
        List<UserVO> vos = new ArrayList<>();
        for (User user : result.getRecords()) {
            vos.add(toVO(user));
        }
        return R.ok(new PageResult<>(vos, result.getTotal(), page, size));
    }

    @Override
    public R<Void> adminToggleStatus(Long userId, Integer status) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return R.fail(404, "User not found");
        }
        user.setStatus(status);
        userMapper.updateById(user);
        stringRedisTemplate.delete("user:profile:" + userId);
        return R.ok();
    }

    @Override
    public R<Void> adminUpdateRole(Long userId, String role) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return R.fail(404, "User not found");
        }
        user.setRole(role);
        userMapper.updateById(user);
        stringRedisTemplate.delete("user:profile:" + userId);
        return R.ok();
    }

    @Override
    public R<Void> adminResetPassword(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return R.fail(404, "User not found");
        }
        user.setPassword(BCrypt.hashpw("123456"));
        userMapper.updateById(user);
        return R.ok();
    }

    @Override
    public R<Map<String, Object>> adminGetStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userMapper.selectCount(null));
        // Today's new users (count users created today)
        stats.put("todayNewUsers", userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .ge(User::getCreateTime, java.time.LocalDate.now().atStartOfDay())));
        stats.put("totalFriends", friendRelationMapper.selectCount(
                new LambdaQueryWrapper<FriendRelation>().eq(FriendRelation::getStatus, 1)));
        return R.ok(stats);
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setPhone(user.getPhone());
        vo.setRole(user.getRole());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}

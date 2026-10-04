package com.mkp.api.fallback;

import com.mkp.api.UserFeignClient;
import com.mkp.common.dto.UserVO;
import com.mkp.common.result.R;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class UserFeignClientFallback implements UserFeignClient {

    @Override
    public R<UserVO> getById(Long id) {
        UserVO vo = new UserVO();
        vo.setId(id);
        vo.setNickname("Unknown");
        return R.ok(vo);
    }

    @Override
    public R<Boolean> areFriends(Long user1, Long user2) {
        return R.ok(false);
    }

    @Override
    public R<Map<Long, UserVO>> getByIds(List<Long> ids) {
        Map<Long, UserVO> map = new HashMap<>();
        for (Long id : ids) {
            UserVO vo = new UserVO();
            vo.setId(id);
            vo.setNickname("Unknown");
            map.put(id, vo);
        }
        return R.ok(map);
    }

    @Override
    public R<List<Long>> searchUserIds(String keyword, Integer limit) {
        // 用户服务不可达时返回空列表：帖子搜索退化为「只按文案搜」
        return R.ok(new ArrayList<>());
    }
}

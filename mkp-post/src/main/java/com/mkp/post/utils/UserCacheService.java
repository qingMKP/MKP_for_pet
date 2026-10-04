package com.mkp.post.utils;

import cn.hutool.json.JSONUtil;
import com.mkp.api.UserFeignClient;
import com.mkp.common.dto.UserVO;
import com.mkp.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
public class UserCacheService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private UserFeignClient userFeignClient;

    public UserVO getUserById(Long userId) {
        String key = "feign:user:" + userId;
        String jsonResult = stringRedisTemplate.opsForValue().get(key);
        if (jsonResult != null) {
            return JSONUtil.toBean(jsonResult, UserVO.class);
        }

        try {
            R<UserVO> r = userFeignClient.getById(userId);
            if (r != null && r.getCode() == 200 && r.getData() != null) {
                UserVO vo = r.getData();
                stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(vo), 30, TimeUnit.MINUTES);
                return vo;
            }
        } catch (Exception e) {
            log.error("Feign调用失败, userId: {}", userId, e);
        }
        return null;
    }

    public Map<Long, UserVO> getByIds(List<Long> ids) {
        Map<Long, UserVO> resultMap = new HashMap<>();

        // 第一步：构造所有 Redis key
        List<String> keys = ids.stream()
                .map(id -> "feign:user:" + id)
                .collect(Collectors.toList());

        // 第二步：批量从 Redis 查
        List<String> jsonList = stringRedisTemplate.opsForValue().multiGet(keys);

        // 第三步：分出命中的和没命中的
        List<Long> missIds = new ArrayList<>();
        if (jsonList != null) {
            for (int i = 0; i < ids.size(); i++) {
                String json = jsonList.get(i);
                if (json != null) {
                    resultMap.put(ids.get(i), JSONUtil.toBean(json, UserVO.class));
                } else {
                    missIds.add(ids.get(i));
                }
            }
        }

        // 第四步：没命中的调 Feign 批量查询
        if (!missIds.isEmpty()) {
            try {
                R<Map<Long, UserVO>> r = userFeignClient.getByIds(missIds);
                if (r != null && r.getCode() == 200 && r.getData() != null) {
                    Map<Long, UserVO> feignResult = r.getData();
                    // 写入 Redis
                    for (Map.Entry<Long, UserVO> entry : feignResult.entrySet()) {
                        String key = "feign:user:" + entry.getKey();
                        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(entry.getValue()), 30, TimeUnit.MINUTES);
                    }
                    resultMap.putAll(feignResult);
                }
            } catch (Exception e) {
                log.error("Feign批量调用失败, ids: {}", missIds, e);
            }
        }

        return resultMap;
    }
}

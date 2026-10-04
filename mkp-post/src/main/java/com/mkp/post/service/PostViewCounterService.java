package com.mkp.post.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 浏览量计数器：请求路径只打 Redis，不碰 DB。
 * Redis 计数器只存"距上次刷库以来的增量"，加法语义由 DB 原子 SQL 兜底。
 */
@Service
public class PostViewCounterService {

    public static final String VIEW_KEY_PREFIX = "post:view:";
    public static final String DIRTY_SET_KEY = "post:view:dirty";

    private static final long COUNTER_TTL_SECONDS = 6 * 60 * 60;
    private static final long DIRTY_TTL_SECONDS = 24 * 60 * 60;

    private final StringRedisTemplate stringRedisTemplate;

    public PostViewCounterService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public static String viewKey(Long postId) {
        return VIEW_KEY_PREFIX + postId;
    }

    /** 请求路径入口：INCR 原子吸收并发 + SADD 脏集合记账通知刷库任务 */
    public void increment(Long postId) {
        String key = viewKey(postId);
        Long value = stringRedisTemplate.opsForValue().increment(key);
        // 只有新建的计数器才设 TTL，避免每个请求都多一次 expire 往返
        if (value != null && value == 1) {
            stringRedisTemplate.expire(key, COUNTER_TTL_SECONDS, TimeUnit.SECONDS);
        }
        // 记入脏集合：同一帖子无论被浏览多少次，集合里只留一个 id
        // add 返回值 = 实际新增成员数，>0 说明是新元素（set 里第一次出现该帖子）
        Long added = stringRedisTemplate.opsForSet().add(DIRTY_SET_KEY, postId.toString());
        if (added != null && added > 0) {
            stringRedisTemplate.expire(DIRTY_SET_KEY, DIRTY_TTL_SECONDS, TimeUnit.SECONDS);
        }
    }

    /** 自上次刷库以来还没落库的增量（详情页展示实时浏览量用） */
    public long pendingDelta(Long postId) {
        String value = stringRedisTemplate.opsForValue().get(viewKey(postId));
        return value == null ? 0 : Long.parseLong(value);
    }
}

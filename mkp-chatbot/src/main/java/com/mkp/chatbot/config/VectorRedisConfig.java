package com.mkp.chatbot.config;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.Connection;
import redis.clients.jedis.JedisPooled;

/**
 * Redis Stack RediSearch 连接池（JedisPooled）
 *
 * 常规 Redis 操作（StringRedisTemplate）继续使用 Lettuce。
 * 这里单独创建 JedisPooled 给 RediSearch 向量索引命令（FT.CREATE / FT.SEARCH）。
 * JedisPooled 是一个轻量连接池，每个操作自动借还连接，无需手动 try-with-resources。
 */
@Configuration
public class VectorRedisConfig {

    @Bean
    public JedisPooled jedisPooled(@Value("${spring.redis.host:127.0.0.1}") String host,
                                   @Value("${spring.redis.port:6379}") int port,
                                   @Value("${spring.redis.password:}") String password) {
        int defaultTimeout = 2000; // ms
        GenericObjectPoolConfig<Connection> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(8);
        config.setMaxIdle(8);
        config.setMinIdle(0);

        if (password != null && !password.isEmpty()) {
            return new JedisPooled(config, host, port, defaultTimeout, null, password);
        }
        return new JedisPooled(config, host, port, defaultTimeout);
    }
}

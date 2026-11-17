package com.mkp.chatbot.config;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.ConnectConfig.ConnectConfigBuilder;
import io.milvus.v2.client.MilvusClientV2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 客户端。
 *
 * MilvusClientV2 内部维护 gRPC channel 和元数据缓存，必须做单例，不能每次调用 new。
 * 项目里长文档子块、FAQ 知识两组向量共用这一个客户端。
 */
@Slf4j
@Configuration
public class MilvusVectorConfig {

    @Bean(destroyMethod = "close")
    public MilvusClientV2 milvusClient(
            @Value("${mkp.knowledge.vector.milvus.uri:http://127.0.0.1:19530}") String uri,
            @Value("${mkp.knowledge.vector.milvus.token:}") String token,
            @Value("${mkp.knowledge.vector.milvus.connect-timeout-ms:5000}") long connectTimeoutMs) {
        ConnectConfigBuilder builder = ConnectConfig.builder()
                .uri(uri)
                .connectTimeoutMs(connectTimeoutMs);
        if (token != null && !token.isBlank()) {
            builder.token(token);
        }
        log.info("Milvus 客户端初始化: uri={} auth={}", uri, (token != null && !token.isBlank()));
        return new MilvusClientV2(builder.build());
    }
}

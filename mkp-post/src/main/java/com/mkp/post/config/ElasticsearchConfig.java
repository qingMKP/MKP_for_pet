package com.mkp.post.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Arrays;

/**
 * Elasticsearch 7.17 客户端配置。
 *
 * Spring Boot 3 已移除 RestHighLevelClient 自动配置，这里显式创建。
 * 连接参数沿用 application.yaml 里的 spring.elasticsearch.* 配置。
 */
@Configuration
public class ElasticsearchConfig {

    @Value("${spring.elasticsearch.uris:http://127.0.0.1:9200}")
    private String uris;

    @Value("${spring.elasticsearch.connection-timeout:3s}")
    private Duration connectionTimeout;

    @Value("${spring.elasticsearch.socket-timeout:10s}")
    private Duration socketTimeout;

    @Bean(destroyMethod = "close")
    public RestHighLevelClient restHighLevelClient() {
        HttpHost[] hosts = Arrays.stream(uris.split(","))
                .map(String::trim)
                .filter(uri -> !uri.isEmpty())
                .map(HttpHost::create)
                .toArray(HttpHost[]::new);

        RestClientBuilder builder = RestClient.builder(hosts)
                .setRequestConfigCallback(config -> config
                        .setConnectTimeout((int) connectionTimeout.toMillis())
                        .setSocketTimeout((int) socketTimeout.toMillis()));

        return new RestHighLevelClient(builder);
    }
}
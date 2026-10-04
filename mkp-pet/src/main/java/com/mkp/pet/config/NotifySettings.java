package com.mkp.pet.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 站内通知（过期提醒私信）配置。
 *
 * enabled          总开关
 * preStartHours    开始前多少小时发"预告"私信
 * scanLimit        每轮最多处理多少笔（分页限速）
 * maxRetry         同一条通知最多尝试几次
 * chatBaseUrl      mkp-chat 地址（走服务间直连，不经网关；集群部署时改成服务地址或换成 Feign lb://mkp-chat）
 * internalToken    调 mkp-chat 内部接口的令牌，两边配置必须一致
 */
@Component
public class NotifySettings {

    @Value("${mkp.notify.enabled:true}")
    private boolean enabled;

    @Value("${mkp.notify.pre-start-hours:24}")
    private int preStartHours;

    @Value("${mkp.notify.scan-limit:200}")
    private int scanLimit;

    @Value("${mkp.notify.max-retry:3}")
    private int maxRetry;

    @Value("${mkp.chat.base-url:http://localhost:8084}")
    private String chatBaseUrl;

    @Value("${mkp.internal.token:}")
    private String internalToken;

    public boolean isEnabled() { return enabled; }
    public int getPreStartHours() { return preStartHours; }
    public int getScanLimit() { return scanLimit; }
    public int getMaxRetry() { return maxRetry; }
    public String getChatBaseUrl() { return chatBaseUrl; }
    public String getInternalToken() { return internalToken; }
}

package com.mkp.pet.client;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.mkp.common.exception.BizException;
import com.mkp.pet.config.NotifySettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 调 mkp-chat 的内部接口发"系统通知"私信。
 * 用 RestTemplate 直连（不经网关、不依赖服务发现），地址来自 mkp.chat.base-url；
 * 失败一律抛 BizException，由调用方落 last_error 并留待下一轮重试。
 */
@Component
public class ChatNotifyClient {

    private static final Logger log = LoggerFactory.getLogger(ChatNotifyClient.class);

    private final NotifySettings settings;
    private final RestTemplate restTemplate;

    public ChatNotifyClient(NotifySettings settings) {
        this.settings = settings;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(8000);
        this.restTemplate = new RestTemplate(factory);
    }

    public void sendSystemMessage(Long toUserId, String content) {
        String url = settings.getChatBaseUrl() + "/internal/message/system";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StrUtil.isNotBlank(settings.getInternalToken())) {
            headers.set("X-Internal-Token", settings.getInternalToken());
        }
        Map<String, Object> body = new HashMap<>();
        body.put("toUserId", toUserId);
        body.put("content", content);

        ResponseEntity<String> resp;
        try {
            resp = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
        } catch (Exception e) {
            throw new BizException("调用 mkp-chat 发送私信失败：" + e.getMessage());
        }
        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new BizException("调用 mkp-chat 发送私信失败：HTTP " + resp.getStatusCode());
        }
        JSONObject json = JSONUtil.parseObj(resp.getBody());
        if (json.getInt("code", 500) != 200) {
            throw new BizException("发送私信被拒绝：" + json.getStr("message", ""));
        }
        log.info("系统私信已发送：toUserId={} url={}", toUserId, url);
    }
}

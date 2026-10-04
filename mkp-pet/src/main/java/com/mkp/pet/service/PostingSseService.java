package com.mkp.pet.service;

import cn.hutool.json.JSONUtil;
import com.mkp.common.dto.PostingVO;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 交易广场 SSE 推送：维护所有在线客户端连接，发布单上架/下架/被匹配时广播最新开放列表。
 */
@Service
public class PostingSseService {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    public void send(SseEmitter emitter, List<PostingVO> postings) {
        try {
            emitter.send(SseEmitter.event().name("postings").data(JSONUtil.toJsonStr(postings)));
        } catch (Exception ignored) {
            emitters.remove(emitter);
        }
    }

    public void broadcast(List<PostingVO> postings) {
        String data = JSONUtil.toJsonStr(postings);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("postings").data(data));
            } catch (Exception ignored) {
                emitters.remove(emitter);
            }
        }
    }
}

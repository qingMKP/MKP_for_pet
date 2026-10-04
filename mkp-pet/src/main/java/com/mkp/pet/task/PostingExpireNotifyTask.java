package com.mkp.pet.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mkp.common.constant.NotifyConstants;
import com.mkp.common.entity.Posting;
import com.mkp.common.entity.PostingNotify;
import com.mkp.pet.client.ChatNotifyClient;
import com.mkp.pet.config.NotifySettings;
import com.mkp.pet.mapper.PostingMapper;
import com.mkp.pet.mapper.PostingNotifyMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 过期交易帖子的站内私信提醒（第一阶段：只发私信，不发短信/推送/邮件）。
 *
 * 两类通知（都只针对寄养单，出售单没有时间概念，暂不处理）：
 *   1) 开始前预告（默认 T-24h）：开始后市场就不再展示这条帖子，先提醒发布者确认或下架；
 *   2) 已过期（开始时间已过、仍挂在"开放中"）：告知市场已停止展示，并给出改期/下架的动作提示。
 *
 * 只发一次：靠 mkp_posting_notify 的 uk(posting_id, notify_type) + claim() 抢占发送权；
 * 发送失败回到"待发送"等下一轮重试，重试次数用尽后放弃并留 last_error 供人工排查。
 */
@Component
public class PostingExpireNotifyTask {

    private static final Logger log = LoggerFactory.getLogger(PostingExpireNotifyTask.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final PostingMapper postingMapper;
    private final PostingNotifyMapper notifyMapper;
    private final ChatNotifyClient chatNotifyClient;
    private final NotifySettings notifySettings;

    public PostingExpireNotifyTask(PostingMapper postingMapper, PostingNotifyMapper notifyMapper,
                                   ChatNotifyClient chatNotifyClient, NotifySettings notifySettings) {
        this.postingMapper = postingMapper;
        this.notifyMapper = notifyMapper;
        this.chatNotifyClient = chatNotifyClient;
        this.notifySettings = notifySettings;
    }

    @Scheduled(fixedDelayString = "${mkp.notify.scan-delay-ms:600000}")
    public void scan() {
        if (!notifySettings.isEnabled()) {
            return;
        }
        notifyPreStart();
        notifyExpired();
    }

    private void notifyPreStart() {
        List<Long> ids = postingMapper.findPreStartCandidates(
                notifySettings.getPreStartHours(), notifySettings.getScanLimit());
        int sent = 0;
        for (Long id : ids) {
            Posting posting = postingMapper.selectById(id);
            if (posting == null || posting.getStartDate() == null) {
                continue;
            }
            String content = "【MKP 官方】你的寄养单「" + posting.getPetName() + "」将于 "
                    + posting.getStartDate().format(FMT) + " 开始。开始后市场会停止展示这条帖子、申请人也无法再申请；"
                    + "如计划有变，请到「我的交易」里改期后重新上架，或直接下架。";
            if (deliver(posting, NotifyConstants.NOTIFY_PRE_START, content)) {
                sent++;
            }
        }
        if (sent > 0) {
            log.info("发布单提醒：开始前预告私信 {} 条（提前 {} 小时）", sent, notifySettings.getPreStartHours());
        }
    }

    private void notifyExpired() {
        List<Long> ids = postingMapper.findExpiredCandidates(notifySettings.getScanLimit());
        int sent = 0;
        for (Long id : ids) {
            Posting posting = postingMapper.selectById(id);
            if (posting == null || posting.getStartDate() == null) {
                continue;
            }
            String content = "【MKP 官方】你的寄养单「" + posting.getPetName() + "」已于 "
                    + posting.getStartDate().format(FMT) + " 开始，市场已停止展示、申请人也不能再申请。"
                    + "如需继续发布，请修改开始时间后重新上架；不打算继续请点下架。";
            if (deliver(posting, NotifyConstants.NOTIFY_EXPIRED, content)) {
                sent++;
            }
        }
        if (sent > 0) {
            log.info("发布单提醒：已过期私信 {} 条", sent);
        }
    }

    /** 幂等建台账 → 抢占发送权 → 发私信 → 回写状态；返回 true 表示这次真的发出去了 */
    private boolean deliver(Posting posting, int notifyType, String content) {
        try {
            PostingNotify row = new PostingNotify();
            row.setPostingId(posting.getId());
            row.setToUserId(posting.getUserId());
            row.setNotifyType(notifyType);
            row.setStatus(0);
            row.setRetryCount(0);
            row.setLastError("");
            notifyMapper.insert(row);
        } catch (DuplicateKeyException ignored) {
            // 台账已存在（同一帖子同类通知只建一次），继续走抢占逻辑
        }
        PostingNotify row = notifyMapper.selectOne(new LambdaQueryWrapper<PostingNotify>()
                .eq(PostingNotify::getPostingId, posting.getId())
                .eq(PostingNotify::getNotifyType, notifyType));
        if (row == null || (row.getStatus() != null && row.getStatus() == 1)) {
            return false;
        }
        if (notifyMapper.claim(row.getId(), notifySettings.getMaxRetry()) == 0) {
            return false;
        }
        try {
            chatNotifyClient.sendSystemMessage(posting.getUserId(), content);
            notifyMapper.markSent(row.getId());
            return true;
        } catch (Exception e) {
            String err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            notifyMapper.markFailed(row.getId(), err.length() > 300 ? err.substring(0, 300) : err);
            log.warn("系统私信发送失败：postingId={} notifyType={} err={}", posting.getId(), notifyType, err);
            return false;
        }
    }
}

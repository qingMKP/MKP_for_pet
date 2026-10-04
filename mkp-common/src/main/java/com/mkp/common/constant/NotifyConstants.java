package com.mkp.common.constant;

/**
 * 站内通知（系统私信）常量。
 *
 * 系统私信用 mkp_message.from_user_id = 0 表示发送方是"MKP 官方"（mkp_user 里没有这个用户，
 * 所以 mkp-chat 的会话列表要单独补一条固定会话，否则用户看不到这条私信）。
 */
public class NotifyConstants {

    /** 系统账号 ID：0 = MKP 官方（不是真实用户） */
    public static final long SYSTEM_USER_ID = 0L;

    /** 消息类型：1 = 系统通知（前端据此显示"MKP 官方"，且不允许回复） */
    public static final int MESSAGE_TYPE_SYSTEM = 1;

    /** 通知类型：开始前预告 */
    public static final int NOTIFY_PRE_START = 1;
    /** 通知类型：已过期（开始时间已过但仍挂在"开放中"） */
    public static final int NOTIFY_EXPIRED = 2;

    private NotifyConstants() {}
}

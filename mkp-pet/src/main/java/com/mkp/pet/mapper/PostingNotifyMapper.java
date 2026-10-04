package com.mkp.pet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkp.common.entity.PostingNotify;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 发布单通知台账 Mapper。
 * 状态流转都是带条件的 UPDATE（抢占发送权、标记已发/失败），保证多实例下也只有一个发送方。
 */
@Mapper
public interface PostingNotifyMapper extends BaseMapper<PostingNotify> {

    /**
     * 抢占发送权：只有"待发送"、或"发送中"卡住超过 10 分钟的记录能被抢占，
     * 且重试次数没用完；返回 1 表示抢到了，可以发。
     */
    @Update("UPDATE mkp_posting_notify SET status = 2, retry_count = retry_count + 1 "
            + "WHERE id = #{id} AND retry_count < #{maxRetry} "
            + "AND (status = 0 OR (status = 2 AND update_time < DATE_SUB(NOW(), INTERVAL 10 MINUTE)))")
    int claim(@Param("id") Long id, @Param("maxRetry") int maxRetry);

    /** 发送成功 */
    @Update("UPDATE mkp_posting_notify SET status = 1, sent_time = NOW(), last_error = '' WHERE id = #{id}")
    int markSent(@Param("id") Long id);

    /** 发送失败：回到待发送，等下一轮重试（错误信息留痕） */
    @Update("UPDATE mkp_posting_notify SET status = 0, last_error = #{lastError} WHERE id = #{id}")
    int markFailed(@Param("id") Long id, @Param("lastError") String lastError);
}

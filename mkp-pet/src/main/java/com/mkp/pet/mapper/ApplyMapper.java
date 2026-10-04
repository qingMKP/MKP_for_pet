package com.mkp.pet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkp.common.entity.Apply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ApplyMapper extends BaseMapper<Apply> {

    /** 原子状态流转：仅当状态等于 from 时才更新为 to */
    @Update("UPDATE mkp_apply SET status = #{to} WHERE id = #{id} AND status = #{from} AND deleted = 0")
    int casStatus(@Param("id") Long id, @Param("from") Integer from, @Param("to") Integer to);

    /** 批量拒绝同一发布单下的其余待确认申请（接受其中一个时调用） */
    @Update("UPDATE mkp_apply SET status = #{to} WHERE posting_id = #{postingId} AND status = #{from} AND id != #{excludeId} AND deleted = 0")
    int rejectOthers(@Param("postingId") Long postingId, @Param("from") Integer from,
                     @Param("to") Integer to, @Param("excludeId") Long excludeId);

    /**
     * 复用同一条申请记录重新申请（被拒 2 / 已撤回 3 -> 待确认 0，同时刷新留言）。
     * 有了 uk_posting_applicant(posting_id, applicant_id) 之后，一人一单只有一行，
     * "被拒了再申请"必须走这里，否则会撞唯一键。
     */
    @Update("UPDATE mkp_apply SET status = 0, message = #{message} "
            + "WHERE id = #{id} AND status IN (2, 3) AND deleted = 0")
    int reactivate(@Param("id") Long id, @Param("message") String message);
}

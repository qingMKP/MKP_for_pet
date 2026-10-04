package com.mkp.pet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkp.common.entity.Posting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PostingMapper extends BaseMapper<Posting> {

    /**
     * 原子状态流转（compare-and-swap）：仅当当前状态等于 from 时才更新为 to。
     * 返回影响行数，0 表示状态已变（并发下被抢占），用于防超卖/防重复匹配。
     */
    @Update("UPDATE mkp_posting SET status = #{to} WHERE id = #{id} AND status = #{from} AND deleted = 0")
    int casStatus(@Param("id") Long id, @Param("from") Integer from, @Param("to") Integer to);

    /** 待预告：寄养、仍"开放中"、开始时间落在 (now, now+hours]（开始后市场就不展示了，所以要提前告知） */
    @Select("SELECT id FROM mkp_posting WHERE category = 2 AND status = 1 AND deleted = 0 "
            + "AND start_date IS NOT NULL AND start_date > NOW() "
            + "AND start_date <= DATE_ADD(NOW(), INTERVAL #{hours} HOUR) "
            + "ORDER BY start_date LIMIT #{limit}")
    List<Long> findPreStartCandidates(@Param("hours") int hours, @Param("limit") int limit);

    /** 已过期：寄养、仍"开放中"、开始时间已过（市场已不再展示它，申请人也无法再申请） */
    @Select("SELECT id FROM mkp_posting WHERE category = 2 AND status = 1 AND deleted = 0 "
            + "AND start_date IS NOT NULL AND start_date <= NOW() ORDER BY start_date LIMIT #{limit}")
    List<Long> findExpiredCandidates(@Param("limit") int limit);
}

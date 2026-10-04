package com.mkp.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkp.common.entity.Post;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PostMapper extends BaseMapper<Post> {

    /** 原子累加浏览量：+delta 在 DB 内部完成，由行锁保证并发安全，避免读-改-写丢失更新 */
    @Update("UPDATE mkp_post SET view_count = view_count + #{delta} WHERE id = #{postId} AND deleted = 0")
    int incrementViewCount(@Param("postId") Long postId, @Param("delta") Integer delta);

    /** 原子 +1 点赞数：基于当前行值计算，行锁保证串行，不丢更新 */
    @Update("UPDATE mkp_post SET like_count = like_count + 1 WHERE id = #{postId} AND deleted = 0")
    int incrementLikeCount(@Param("postId") Long postId);

    /** 原子 -1 点赞数：like_count > 0 防多减为负数 */
    @Update("UPDATE mkp_post SET like_count = like_count - 1 WHERE id = #{postId} AND like_count > 0 AND deleted = 0")
    int decrementLikeCount(@Param("postId") Long postId);
}

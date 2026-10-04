package com.mkp.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkp.common.entity.Comment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
}

package com.mkp.pet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkp.common.entity.TradeEvent;
import org.apache.ibatis.annotations.Mapper;

/** 审计表：只有查询与插入，没有 update/delete（刻意的） */
@Mapper
public interface TradeEventMapper extends BaseMapper<TradeEvent> {
}

package com.mkp.pet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkp.common.entity.WalletLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 钱包流水 Mapper：账本只插入、只查询，没有任何 update/delete 方法——这是刻意的。
 * 要改账户状态就再写一条流水，不能改历史。
 */
@Mapper
public interface WalletLogMapper extends BaseMapper<WalletLog> {
}

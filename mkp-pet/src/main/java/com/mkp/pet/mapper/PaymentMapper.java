package com.mkp.pet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkp.common.entity.Payment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付单 Mapper。
 * 本版只用 insert / select：虚拟钱包同步扣款，"成功"就是最终态，不需要 CAS 改状态。
 * 将来接真渠道时在这里补一个 casStatus(from -> to)，把支付单从"处理中"改成"成功"。
 */
@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {
}

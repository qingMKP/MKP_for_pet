package com.mkp.pet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkp.common.entity.Wallet;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 钱包账户 Mapper：钱的每一次搬迁都是这里的一条条件 UPDATE。
 *
 * 返回 0 行 = 条件不满足（余额/冻结不够、或行不存在），调用方必须当成失败处理并让事务回滚。
 * 不允许"先 select 查余额再判断"——读后写在并发下会双双通过，条件 UPDATE 才有原子性。
 */
@Mapper
public interface WalletMapper extends BaseMapper<Wallet> {

    /** 账户不存在时补一行 0 元账户（user_id 有唯一键，INSERT IGNORE 天然幂等） */
    @Insert("INSERT IGNORE INTO mkp_wallet (user_id, balance, frozen, version) VALUES (#{userId}, 0.00, 0.00, 0)")
    int ensureRow(@Param("userId") Long userId);

    /** 支付冻结：可用余额 -> 冻结（余额不足返回 0，是"余额不足"的唯一判据） */
    @Update("UPDATE mkp_wallet SET balance = balance - #{amount}, frozen = frozen + #{amount}, version = version + 1 "
            + "WHERE user_id = #{userId} AND balance >= #{amount}")
    int freezeForPay(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /** 结算出账：把付款方的冻结资金扣出去（转给收款方），冻结不足返回 0 */
    @Update("UPDATE mkp_wallet SET frozen = frozen - #{amount}, version = version + 1 "
            + "WHERE user_id = #{userId} AND frozen >= #{amount}")
    int releaseFrozen(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /** 结算入账：收款方可用余额增加（不存在"余额不足"问题） */
    @Update("UPDATE mkp_wallet SET balance = balance + #{amount}, version = version + 1 "
            + "WHERE user_id = #{userId}")
    int addBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /** 退款解冻：冻结 -> 可用，退回给付款方，冻结不足返回 0 */
    @Update("UPDATE mkp_wallet SET frozen = frozen - #{amount}, balance = balance + #{amount}, version = version + 1 "
            + "WHERE user_id = #{userId} AND frozen >= #{amount}")
    int refundFrozen(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /** 手动设置可用余额（仅调试入口 /api/admin/wallet/set 使用） */
    @Update("UPDATE mkp_wallet SET balance = #{balance}, version = version + 1 WHERE user_id = #{userId}")
    int setBalance(@Param("userId") Long userId, @Param("balance") BigDecimal balance);
}

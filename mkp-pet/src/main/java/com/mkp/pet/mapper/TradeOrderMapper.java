package com.mkp.pet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkp.common.entity.TradeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface TradeOrderMapper extends BaseMapper<TradeOrder> {

    /** 原子支付：0待支付 -> 1托管中，防重复支付 */
    @Update("UPDATE mkp_trade_order SET status = 1, pay_time = NOW() WHERE id = #{id} AND status = 0 AND deleted = 0")
    int markPaid(@Param("id") Long id);

    /** 原子开始履约：1托管中 -> 2履约中 */
    @Update("UPDATE mkp_trade_order SET status = 2 WHERE id = #{id} AND status = 1 AND deleted = 0")
    int markStart(@Param("id") Long id);

    /** 原子结束履约：2履约中 -> 3待结算 */
    @Update("UPDATE mkp_trade_order SET status = 3 WHERE id = #{id} AND status = 2 AND deleted = 0")
    int markFinish(@Param("id") Long id);

    /** 原子完成：from -> 4已完成（寄养 from=3 待结算；出售 from=1 托管中） */
    @Update("UPDATE mkp_trade_order SET status = 4, complete_time = NOW() WHERE id = #{id} AND status = #{from} AND deleted = 0")
    int markComplete(@Param("id") Long id, @Param("from") Integer from);

    /** 原子取消：0待支付 -> 5已取消 */
    @Update("UPDATE mkp_trade_order SET status = 5 WHERE id = #{id} AND status = 0 AND deleted = 0")
    int markCancelled(@Param("id") Long id);

    /** 原子退款：1托管中 -> 6已退款 */
    @Update("UPDATE mkp_trade_order SET status = 6 WHERE id = #{id} AND status = 1 AND deleted = 0")
    int markRefunded(@Param("id") Long id);

    /** 超时关单：只有"仍待支付且已到期"的订单才会被取消（到期时间是硬条件，防止误关） */
    @Update("UPDATE mkp_trade_order SET status = 5 WHERE id = #{id} AND status = 0 "
            + "AND expire_time IS NOT NULL AND expire_time < NOW() AND deleted = 0")
    int markExpired(@Param("id") Long id);

    /** 待支付且已超时的订单 ID（关单任务扫描用，分页限速） */
    @Select("SELECT id FROM mkp_trade_order WHERE status = 0 AND expire_time IS NOT NULL AND expire_time < NOW() "
            + "AND deleted = 0 ORDER BY id LIMIT #{limit}")
    List<Long> findExpiredIds(@Param("limit") int limit);

    /** 出售：已付款但买家超过 N 小时未确认收货的订单 ID（自动确认用） */
    @Select("SELECT id FROM mkp_trade_order WHERE status = 1 AND category = 3 AND pay_time IS NOT NULL "
            + "AND pay_time < DATE_SUB(NOW(), INTERVAL #{hours} HOUR) AND deleted = 0 ORDER BY id LIMIT #{limit}")
    List<Long> findSaleToAutoConfirmIds(@Param("hours") int hours, @Param("limit") int limit);

    /** 寄养：已进入"待结算"但主人超过 N 小时未确认的订单 ID（自动结算用） */
    @Select("SELECT id FROM mkp_trade_order WHERE status = 3 "
            + "AND update_time < DATE_SUB(NOW(), INTERVAL #{hours} HOUR) AND deleted = 0 ORDER BY id LIMIT #{limit}")
    List<Long> findSettlingToAutoSettleIds(@Param("hours") int hours, @Param("limit") int limit);
}

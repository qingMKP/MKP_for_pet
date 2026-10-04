package com.mkp.common.exception;

import com.mkp.common.result.R;

/**
 * 业务异常：用于"必须回滚事务、同时要把原因告诉用户"的场景。
 *
 * 为什么一定要抛异常、不能 return R.fail()：
 * 资金动作是在 @Transactional 里跟订单状态 CAS 一起做的。如果扣款失败时只 return，
 * 前面那句订单 UPDATE 就会被正常提交，产生"订单已托管但没扣钱"的脏数据。
 * 抛 RuntimeException 才能让 Spring 回滚整个事务。
 *
 * 抛出后由 GlobalExceptionHandler 转成 R.fail(code, message) 返回，前端照常弹提示。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(R.ERROR, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}

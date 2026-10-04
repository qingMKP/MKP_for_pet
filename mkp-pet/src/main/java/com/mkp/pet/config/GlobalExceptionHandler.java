package com.mkp.pet.config;

import com.mkp.common.exception.BizException;
import com.mkp.common.result.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 只接管 BizException：把它翻译成前端认识的 R.fail(code, message)。
 *
 * 注意时机——异常抛出时 Spring 已经回滚了 @Transactional 里做过的所有写操作，
 * 所以"订单状态被回滚、余额没动"这类一致性由事务保证，这里只负责给出可读的提示。
 * 其他异常不在这里处理，保持项目原有行为（500）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException e) {
        log.warn("业务异常：{}", e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }
}

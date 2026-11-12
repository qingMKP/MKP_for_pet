package com.mkp.pet.controller;

import com.mkp.common.dto.WalletVO;
import com.mkp.common.result.R;
import com.mkp.pet.service.WalletService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    private Long getUserId(HttpServletRequest request) {
        String header = request.getHeader("X-User-Id");
        return header != null ? Long.parseLong(header) : null;
    }

    /** 我的钱包：余额 / 冻结 + 最近流水 */
    @GetMapping("/wallet/me")
    public R<WalletVO> me(HttpServletRequest request,
                          @RequestParam(value = "logLimit", defaultValue = "50") int logLimit) {
        return walletService.myWallet(getUserId(request), logLimit);
    }

    /**
     * 手动设置余额（调试用）。
     * 路径落在 /api/admin/ 下，mkp-gateway 的 AuthGlobalFilter 已经对 /api/admin/ 前缀做 ADMIN 校验，
     * 非管理员在网关层直接 403，这里不需要再写一遍权限判断。
     * body: { "userId": 1, "balance": 1000, "remark": "调试" }，userId 省略时给自己设置。
     */
    @PostMapping("/admin/wallet/set")
    public R<WalletVO> setBalance(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long currentUserId = getUserId(request);
        Long targetId = currentUserId;
        if (body.get("userId") != null) {
            targetId = Long.valueOf(String.valueOf(body.get("userId")));
        }
        Object balance = body.get("balance");
        if (balance == null) {
            return R.fail("balance 不能为空");
        }
        String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        return walletService.setBalance(targetId, new BigDecimal(String.valueOf(balance)), remark);
    }
}

package com.mkp.pet.controller;

import com.mkp.common.dto.ApplySaveDTO;
import com.mkp.common.dto.ApplyVO;
import com.mkp.common.dto.TradeOrderVO;
import com.mkp.common.result.R;
import com.mkp.pet.service.ApplyService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/apply")
public class ApplyController {

    private final ApplyService applyService;

    public ApplyController(ApplyService applyService) {
        this.applyService = applyService;
    }

    private Long getUserId(HttpServletRequest request) {
        String header = request.getHeader("X-User-Id");
        return header != null ? Long.parseLong(header) : null;
    }

    @PostMapping("/posting/{postingId}")
    public R<ApplyVO> apply(@PathVariable Long postingId, @RequestBody ApplySaveDTO dto,
                            HttpServletRequest request) {
        return applyService.apply(getUserId(request), postingId, dto);
    }

    @GetMapping("/received")
    public R<List<ApplyVO>> received(HttpServletRequest request) {
        return applyService.received(getUserId(request));
    }

    @GetMapping("/sent")
    public R<List<ApplyVO>> sent(HttpServletRequest request) {
        return applyService.sent(getUserId(request));
    }

    @PostMapping("/{id}/accept")
    public R<TradeOrderVO> accept(@PathVariable Long id, HttpServletRequest request) {
        return applyService.accept(getUserId(request), id);
    }

    @PostMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id, HttpServletRequest request) {
        return applyService.reject(getUserId(request), id);
    }

    @PostMapping("/{id}/withdraw")
    public R<Void> withdraw(@PathVariable Long id, HttpServletRequest request) {
        return applyService.withdraw(getUserId(request), id);
    }
}

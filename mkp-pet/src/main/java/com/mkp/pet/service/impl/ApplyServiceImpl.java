package com.mkp.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mkp.common.constant.RedisKey;
import com.mkp.common.constant.TradeConstants;
import com.mkp.common.dto.ApplySaveDTO;
import com.mkp.common.dto.ApplyVO;
import com.mkp.common.dto.TradeOrderVO;
import com.mkp.common.entity.Apply;
import com.mkp.common.entity.Posting;
import com.mkp.common.result.R;
import com.mkp.pet.mapper.ApplyMapper;
import com.mkp.pet.mapper.PostingMapper;
import com.mkp.pet.service.ApplyService;
import com.mkp.pet.service.PostingService;
import com.mkp.pet.service.TradeOrderService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ApplyServiceImpl implements ApplyService {

    private final ApplyMapper applyMapper;
    private final PostingMapper postingMapper;
    private final TradeOrderService tradeOrderService;
    private final PostingService postingService;
    private final StringRedisTemplate stringRedisTemplate;

    public ApplyServiceImpl(ApplyMapper applyMapper, PostingMapper postingMapper,
                            TradeOrderService tradeOrderService, PostingService postingService,
                            StringRedisTemplate stringRedisTemplate) {
        this.applyMapper = applyMapper;
        this.postingMapper = postingMapper;
        this.tradeOrderService = tradeOrderService;
        this.postingService = postingService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    @Transactional
    public R<ApplyVO> apply(Long userId, Long postingId, ApplySaveDTO dto) {
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        if (dto.getRequestId() == null || dto.getRequestId().isBlank()) {
            return R.fail("缺少请求标识 requestId");
        }
        // 幂等：同一 requestId 只处理一次
        String tokenKey = RedisKey.POSTING_APPLY_TOKEN_PREFIX + dto.getRequestId();
        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(tokenKey, "1", Duration.ofMinutes(5));
        if (!Boolean.TRUE.equals(first)) {
            return R.fail("请勿重复提交申请");
        }

        Posting posting = postingMapper.selectById(postingId);
        if (posting == null) {
            return R.fail(404, "发布单不存在");
        }
        if (posting.getStatus() != TradeConstants.POSTING_OPEN) {
            return R.fail("该发布单已结束，无法申请");
        }
        if (posting.getStartDate() != null && !posting.getStartDate().isAfter(LocalDateTime.now())) {
            return R.fail("该寄养已开始，无法申请");
        }
        if (posting.getUserId().equals(userId)) {
            return R.fail("不能申请自己发布的单");
        }
        // 价格只由发布单决定：没定价的（历史"面议"单）直接拒绝，避免生成 0 元订单
        if (posting.getPrice() == null) {
            return R.fail("该发布单未设置价格，无法申请");
        }
        String message = dto.getMessage() == null ? "" : dto.getMessage();

        // 一人对同一发布单只有一条申请记录（uk_posting_applicant）：
        //   待确认 → 不能重复提交；已被接受 → 不能再申请；
        //   被拒 / 已撤回 → 复用这条记录重新置为待确认（否则"被拒两次"会撞唯一键报 500）
        Apply mine = applyMapper.selectOne(new LambdaQueryWrapper<Apply>()
                .eq(Apply::getPostingId, postingId)
                .eq(Apply::getApplicantId, userId));
        if (mine != null) {
            if (mine.getStatus() == TradeConstants.APPLY_PENDING) {
                return R.fail("你已申请过该发布单");
            }
            if (mine.getStatus() == TradeConstants.APPLY_ACCEPTED) {
                return R.fail("你已被接受，无法重复申请");
            }
            if (applyMapper.reactivate(mine.getId(), message) == 0) {
                return R.fail("请勿重复提交申请");
            }
            mine.setStatus(TradeConstants.APPLY_PENDING);
            mine.setMessage(message);
            return R.ok(toVO(mine, posting));
        }

        Apply apply = new Apply();
        apply.setPostingId(postingId);
        apply.setApplicantId(userId);
        apply.setMessage(message);
        apply.setStatus(TradeConstants.APPLY_PENDING);
        applyMapper.insert(apply);

        return R.ok(toVO(apply, posting));
    }

    @Override
    public R<List<ApplyVO>> received(Long userId) {
        List<Posting> myPostings = postingMapper.selectList(new LambdaQueryWrapper<Posting>()
                .eq(Posting::getUserId, userId));
        if (myPostings.isEmpty()) {
            return R.ok(List.of());
        }
        List<Long> postingIds = myPostings.stream().map(Posting::getId).collect(Collectors.toList());
        List<Apply> applies = applyMapper.selectList(new LambdaQueryWrapper<Apply>()
                .in(Apply::getPostingId, postingIds)
                .orderByDesc(Apply::getCreateTime));
        Map<Long, Posting> postingMap = myPostings.stream()
                .collect(Collectors.toMap(Posting::getId, p -> p));
        List<ApplyVO> vos = new ArrayList<>();
        for (Apply a : applies) {
            vos.add(toVO(a, postingMap.get(a.getPostingId())));
        }
        return R.ok(vos);
    }

    @Override
    public R<List<ApplyVO>> sent(Long userId) {
        List<Apply> applies = applyMapper.selectList(new LambdaQueryWrapper<Apply>()
                .eq(Apply::getApplicantId, userId)
                .orderByDesc(Apply::getCreateTime));
        List<ApplyVO> vos = new ArrayList<>();
        for (Apply a : applies) {
            Posting posting = postingMapper.selectById(a.getPostingId());
            vos.add(toVO(a, posting));
        }
        return R.ok(vos);
    }

    @Override
    @Transactional
    public R<TradeOrderVO> accept(Long userId, Long applyId) {
        Apply apply = applyMapper.selectById(applyId);
        if (apply == null) {
            return R.fail(404, "申请不存在");
        }
        if (apply.getStatus() != TradeConstants.APPLY_PENDING) {
            return R.fail("该申请已被处理");
        }
        Posting posting = postingMapper.selectById(apply.getPostingId());
        if (posting == null) {
            return R.fail(404, "发布单不存在");
        }
        if (!posting.getUserId().equals(userId)) {
            return R.fail(403, "只有发布者才能接受申请");
        }
        if (posting.getPrice() == null) {
            return R.fail("该发布单未设置价格，无法生成订单");
        }

        // 原子抢占发布单：开放->已匹配，并发下只有一个成功
        int locked = postingMapper.casStatus(posting.getId(),
                TradeConstants.POSTING_OPEN, TradeConstants.POSTING_MATCHED);
        if (locked == 0) {
            return R.fail("手慢了，该发布单已被匹配");
        }
        // 原子接受申请
        int accepted = applyMapper.casStatus(apply.getId(),
                TradeConstants.APPLY_PENDING, TradeConstants.APPLY_ACCEPTED);
        if (accepted == 0) {
            return R.fail("申请状态已变化");
        }
        // 生成订单
        TradeOrderVO orderVO = tradeOrderService.createFromApply(posting, apply);
        // 拒绝其余待确认申请
        applyMapper.rejectOthers(posting.getId(),
                TradeConstants.APPLY_PENDING, TradeConstants.APPLY_REJECTED, apply.getId());
        postingService.broadcastAfterCommit();
        return R.ok(orderVO);
    }

    @Override
    @Transactional
    public R<Void> reject(Long userId, Long applyId) {
        Apply apply = applyMapper.selectById(applyId);
        if (apply == null) {
            return R.fail(404, "申请不存在");
        }
        Posting posting = postingMapper.selectById(apply.getPostingId());
        if (posting == null || !posting.getUserId().equals(userId)) {
            return R.fail(403, "只有发布者才能拒绝申请");
        }
        int rows = applyMapper.casStatus(applyId, TradeConstants.APPLY_PENDING, TradeConstants.APPLY_REJECTED);
        if (rows == 0) {
            return R.fail("申请已被处理，无法拒绝");
        }
        return R.ok();
    }

    @Override
    @Transactional
    public R<Void> withdraw(Long userId, Long applyId) {
        Apply apply = applyMapper.selectById(applyId);
        if (apply == null) {
            return R.fail(404, "申请不存在");
        }
        if (!apply.getApplicantId().equals(userId)) {
            return R.fail(403, "只有申请人才能撤回");
        }
        int rows = applyMapper.casStatus(applyId, TradeConstants.APPLY_PENDING, TradeConstants.APPLY_WITHDRAWN);
        if (rows == 0) {
            return R.fail("申请已被处理，无法撤回");
        }
        return R.ok();
    }

    private ApplyVO toVO(Apply apply, Posting posting) {
        ApplyVO vo = new ApplyVO();
        vo.setId(apply.getId());
        vo.setPostingId(apply.getPostingId());
        vo.setApplicantId(apply.getApplicantId());
        vo.setMessage(apply.getMessage());
        vo.setStatus(apply.getStatus());
        vo.setCreateTime(apply.getCreateTime());
        if (posting != null) {
            vo.setPostingPetName(posting.getPetName());
            String images = posting.getImages();
            vo.setPostingImage(images != null && !images.isEmpty() ? images.split(",")[0] : null);
            vo.setPostingCategory(posting.getCategory());
            vo.setPostingDirection(posting.getDirection());
            vo.setPostingPrice(posting.getPrice());
            vo.setPostingPriceUnit(posting.getPriceUnit());
        }
        return vo;
    }
}

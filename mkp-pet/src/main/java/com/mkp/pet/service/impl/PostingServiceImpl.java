package com.mkp.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mkp.common.constant.TradeConstants;
import com.mkp.common.dto.PostingSaveDTO;
import com.mkp.common.dto.PostingVO;
import com.mkp.common.entity.Posting;
import com.mkp.common.result.R;
import com.mkp.pet.config.TradeSettings;
import com.mkp.pet.mapper.PostingMapper;
import com.mkp.pet.service.PostingService;
import com.mkp.pet.service.PostingSseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PostingServiceImpl implements PostingService {

    private final PostingMapper postingMapper;
    private final PostingSseService postingSseService;
    private final TradeSettings tradeSettings;

    public PostingServiceImpl(PostingMapper postingMapper, PostingSseService postingSseService,
                              TradeSettings tradeSettings) {
        this.postingMapper = postingMapper;
        this.postingSseService = postingSseService;
        this.tradeSettings = tradeSettings;
    }

    @Override
    @Transactional
    public R<PostingVO> createPosting(Long userId, PostingSaveDTO dto) {
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        if (!isValidCategory(dto.getCategory())) {
            return R.fail("业务类型只能选「出售」或「寄养」");
        }
        // 方向不由用户选择：分类决定资金方向（寄养=发布者出钱，出售=买家付钱）
        int direction = TradeConstants.deriveDirection(dto.getCategory());

        // 两类都必须定价：价格是订单金额的唯一来源，不再有"面议 + 申请人报价"
        if (dto.getPrice() == null) {
            return R.fail(dto.getCategory() == TradeConstants.CATEGORY_SALE
                    ? "出售必须填写售价" : "寄养必须填写寄养费");
        }
        if (dto.getCategory() == TradeConstants.CATEGORY_FOSTER && dto.getPriceUnit() == null) {
            return R.fail("请选择计价单位");
        }
        if (dto.getStartDate() != null && dto.getEndDate() != null
                && !dto.getEndDate().isAfter(dto.getStartDate())) {
            return R.fail("结束时间必须晚于开始时间");
        }
        // 寄养的时间约束（三条）：
        //   1) 开始时间必填 —— 没开始时间的寄养既没有时长，也无法判断能不能申请；
        //   2) 开始时间要有提前量 —— 距开始不足 N 分钟（或已过）发布出去就立刻不可见，等于白发；
        //   3) 结束时间必填 —— 申请人要看到明确的寄养周期，按天/小时/分钟计价还要靠它算时长。
        if (dto.getCategory() == TradeConstants.CATEGORY_FOSTER) {
            if (dto.getStartDate() == null) {
                return R.fail("寄养必须填写开始时间");
            }
            LocalDateTime now = LocalDateTime.now();
            int lead = tradeSettings.getMinStartLeadMinutes();
            if (!dto.getStartDate().isAfter(now)) {
                return R.fail("开始寄养时间已过，请重新选择开始时间");
            }
            if (!dto.getStartDate().isAfter(now.plusMinutes(lead))) {
                return R.fail("开始寄养时间距现在不足 " + lead + " 分钟，请至少提前 " + lead + " 分钟发布");
            }
            if (dto.getEndDate() == null) {
                return R.fail("寄养必须填写结束时间");
            }
        }

        Posting posting = new Posting();
        posting.setUserId(userId);
        posting.setDirection(direction);
        posting.setCategory(dto.getCategory());
        posting.setPetName(dto.getPetName() == null ? "" : dto.getPetName());
        posting.setSpecies(dto.getSpecies() == null ? "" : dto.getSpecies());
        posting.setBreed(dto.getBreed() == null ? "" : dto.getBreed());
        posting.setAgeMonth(dto.getAgeMonth() == null ? 0 : dto.getAgeMonth());
        posting.setWeightKg(dto.getWeightKg());
        posting.setGender(dto.getGender() == null ? 0 : dto.getGender());
        posting.setPrice(dto.getPrice());
        posting.setPriceUnit(dto.getPriceUnit() == null ? TradeConstants.PRICE_UNIT_TOTAL : dto.getPriceUnit());
        posting.setStartDate(dto.getStartDate());
        posting.setEndDate(dto.getEndDate());
        posting.setDurationMinutes(calcDurationMinutes(dto));
        posting.setDescription(dto.getDescription() == null ? "" : dto.getDescription());
        posting.setImages(dto.getImages() != null && !dto.getImages().isEmpty()
                ? String.join(",", dto.getImages()) : "");
        posting.setStatus(TradeConstants.POSTING_OPEN);
        postingMapper.insert(posting);

        broadcastAfterCommit();
        return R.ok(toVO(posting));
    }

    /** 当前只允许发布「寄养」和「出售」；领养暂不开放（避免用户把领养和寄养搞混） */
    private boolean isValidCategory(Integer category) {
        return category != null
                && (category == TradeConstants.CATEGORY_FOSTER
                    || category == TradeConstants.CATEGORY_SALE);
    }

    private Integer calcDurationMinutes(PostingSaveDTO dto) {
        if (dto.getDurationMinutes() != null && dto.getDurationMinutes() > 0) {
            return dto.getDurationMinutes();
        }
        if (dto.getStartDate() != null && dto.getEndDate() != null) {
            return (int) ChronoUnit.MINUTES.between(dto.getStartDate(), dto.getEndDate());
        }
        return null;
    }

    @Override
    public R<List<PostingVO>> listOpen(Integer category) {
        return R.ok(listOpenVOs(category));
    }

    @Override
    public List<PostingVO> listOpenVOs() {
        return listOpenVOs(null);
    }

    private List<PostingVO> listOpenVOs(Integer category) {
        LambdaQueryWrapper<Posting> wrapper = new LambdaQueryWrapper<Posting>()
                .eq(Posting::getStatus, TradeConstants.POSTING_OPEN)
                .and(w -> w.isNull(Posting::getStartDate)
                        .or().gt(Posting::getStartDate, LocalDateTime.now()))
                .orderByDesc(Posting::getCreateTime);
        if (category != null) {
            wrapper.eq(Posting::getCategory, category);
        }
        // 不再提供 direction 过滤：分类已经唯一决定了资金方向，把"方向"暴露给用户只会造成混淆
        return postingMapper.selectList(wrapper).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public R<PostingVO> getDetail(Long id) {
        Posting posting = postingMapper.selectById(id);
        if (posting == null) {
            return R.fail(404, "发布单不存在");
        }
        return R.ok(toVO(posting));
    }

    @Override
    @Transactional
    public R<Void> offPosting(Long userId, Long id) {
        Posting posting = postingMapper.selectById(id);
        if (posting == null) {
            return R.fail(404, "发布单不存在");
        }
        if (!posting.getUserId().equals(userId)) {
            return R.fail(403, "无权操作该发布单");
        }
        int rows = postingMapper.casStatus(id, TradeConstants.POSTING_OPEN, TradeConstants.POSTING_OFF);
        if (rows == 0) {
            return R.fail("发布单状态已变化，无法下架");
        }
        broadcastAfterCommit();
        return R.ok();
    }

    @Override
    public void broadcastCurrentState() {
        postingSseService.broadcast(listOpenVOs());
    }

    /**
     * 事务提交后再推送。原来在事务里直接 broadcast，一旦回滚前端已经收到"变了"的消息，
     * 而且推送时读到的可能是未提交数据（脏读）。这里统一改成提交后推送。
     */
    @Override
    public void broadcastAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broadcastCurrentState();
                }
            });
        } else {
            broadcastCurrentState();
        }
    }

    private PostingVO toVO(Posting posting) {
        PostingVO vo = new PostingVO();
        vo.setId(posting.getId());
        vo.setUserId(posting.getUserId());
        vo.setDirection(posting.getDirection());
        vo.setCategory(posting.getCategory());
        vo.setPetName(posting.getPetName());
        vo.setSpecies(posting.getSpecies());
        vo.setBreed(posting.getBreed());
        vo.setAgeMonth(posting.getAgeMonth());
        vo.setWeightKg(posting.getWeightKg());
        vo.setGender(posting.getGender());
        vo.setPrice(posting.getPrice());
        vo.setPriceUnit(posting.getPriceUnit());
        vo.setStartDate(posting.getStartDate());
        vo.setEndDate(posting.getEndDate());
        vo.setDurationMinutes(posting.getDurationMinutes());
        vo.setDescription(posting.getDescription());
        vo.setImages(posting.getImages() != null && !posting.getImages().isEmpty()
                ? Arrays.asList(posting.getImages().split(",")) : List.of());
        vo.setStatus(posting.getStatus());
        vo.setCreateTime(posting.getCreateTime());
        return vo;
    }
}

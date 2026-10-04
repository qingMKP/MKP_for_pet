package com.mkp.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mkp.common.constant.TradeConstants;
import com.mkp.common.dto.ReviewSaveDTO;
import com.mkp.common.dto.ReviewVO;
import com.mkp.common.entity.Review;
import com.mkp.common.entity.TradeOrder;
import com.mkp.common.result.R;
import com.mkp.pet.mapper.ReviewMapper;
import com.mkp.pet.mapper.TradeOrderMapper;
import com.mkp.pet.service.ReviewService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReviewServiceImpl implements ReviewService {

    private final ReviewMapper reviewMapper;
    private final TradeOrderMapper orderMapper;

    public ReviewServiceImpl(ReviewMapper reviewMapper, TradeOrderMapper orderMapper) {
        this.reviewMapper = reviewMapper;
        this.orderMapper = orderMapper;
    }

    @Override
    @Transactional
    public R<ReviewVO> submit(Long userId, ReviewSaveDTO dto) {
        if (dto.getOrderId() == null) {
            return R.fail("订单ID不能为空");
        }
        if (dto.getRating() == null || dto.getRating() < 1 || dto.getRating() > 5) {
            return R.fail("评分需在 1~5 之间");
        }
        TradeOrder order = orderMapper.selectById(dto.getOrderId());
        if (order == null) {
            return R.fail(404, "订单不存在");
        }
        if (order.getStatus() != TradeConstants.ORDER_COMPLETED) {
            return R.fail("订单未完成，无法评价");
        }
        // 评价方必须是订单参与者，被评价方是对方
        Long revieweeId;
        if (order.getPayerId().equals(userId)) {
            revieweeId = order.getPayeeId();
        } else if (order.getPayeeId().equals(userId)) {
            revieweeId = order.getPayerId();
        } else {
            return R.fail(403, "无权评价该订单");
        }
        // 防重复评价
        Long existing = reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                .eq(Review::getOrderId, order.getId())
                .eq(Review::getReviewerId, userId));
        if (existing != null && existing > 0) {
            return R.fail("你已评价过该订单");
        }

        Review review = new Review();
        review.setOrderId(order.getId());
        review.setCategory(order.getCategory());
        review.setReviewerId(userId);
        review.setRevieweeId(revieweeId);
        review.setRating(dto.getRating());
        review.setContent(dto.getContent() == null ? "" : dto.getContent());
        reviewMapper.insert(review);
        return R.ok(toVO(review));
    }

    @Override
    public R<List<ReviewVO>> listByUser(Long userId) {
        List<Review> reviews = reviewMapper.selectList(new LambdaQueryWrapper<Review>()
                .eq(Review::getRevieweeId, userId)
                .orderByDesc(Review::getCreateTime));
        return R.ok(reviews.stream().map(this::toVO).collect(Collectors.toList()));
    }

    private ReviewVO toVO(Review review) {
        ReviewVO vo = new ReviewVO();
        vo.setId(review.getId());
        vo.setOrderId(review.getOrderId());
        vo.setCategory(review.getCategory());
        vo.setReviewerId(review.getReviewerId());
        vo.setRevieweeId(review.getRevieweeId());
        vo.setRating(review.getRating());
        vo.setContent(review.getContent());
        vo.setCreateTime(review.getCreateTime());
        return vo;
    }
}

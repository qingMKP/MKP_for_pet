package com.mkp.pet.controller;

import com.mkp.common.dto.ReviewSaveDTO;
import com.mkp.common.dto.ReviewVO;
import com.mkp.common.result.R;
import com.mkp.pet.service.ReviewService;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/review")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    private Long getUserId(HttpServletRequest request) {
        String header = request.getHeader("X-User-Id");
        return header != null ? Long.parseLong(header) : null;
    }

    @PostMapping
    public R<ReviewVO> submit(@RequestBody ReviewSaveDTO dto, HttpServletRequest request) {
        return reviewService.submit(getUserId(request), dto);
    }

    @GetMapping("/user/{userId}")
    public R<List<ReviewVO>> listByUser(@PathVariable Long userId) {
        return reviewService.listByUser(userId);
    }
}

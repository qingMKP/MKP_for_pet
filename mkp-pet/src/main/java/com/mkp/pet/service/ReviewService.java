package com.mkp.pet.service;

import com.mkp.common.dto.ReviewSaveDTO;
import com.mkp.common.dto.ReviewVO;
import com.mkp.common.result.R;

import java.util.List;

public interface ReviewService {
    R<ReviewVO> submit(Long userId, ReviewSaveDTO dto);
    R<List<ReviewVO>> listByUser(Long userId);
}

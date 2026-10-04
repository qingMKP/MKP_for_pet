package com.mkp.pet.service;

import com.mkp.common.dto.ApplySaveDTO;
import com.mkp.common.dto.ApplyVO;
import com.mkp.common.dto.TradeOrderVO;
import com.mkp.common.result.R;

import java.util.List;

public interface ApplyService {
    R<ApplyVO> apply(Long userId, Long postingId, ApplySaveDTO dto);
    R<List<ApplyVO>> received(Long userId);
    R<List<ApplyVO>> sent(Long userId);
    R<TradeOrderVO> accept(Long userId, Long applyId);
    R<Void> reject(Long userId, Long applyId);
    R<Void> withdraw(Long userId, Long applyId);
}

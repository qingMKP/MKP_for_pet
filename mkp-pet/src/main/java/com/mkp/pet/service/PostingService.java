package com.mkp.pet.service;

import com.mkp.common.dto.PostingSaveDTO;
import com.mkp.common.dto.PostingVO;
import com.mkp.common.result.R;

import java.util.List;

public interface PostingService {
    R<PostingVO> createPosting(Long userId, PostingSaveDTO dto);
    R<List<PostingVO>> listOpen(Integer category);
    R<PostingVO> getDetail(Long id);
    R<Void> offPosting(Long userId, Long id);
    List<PostingVO> listOpenVOs();
    void broadcastCurrentState();

    /** 事务提交后再推送（事务回滚时不推送）；无事务时立即推送 */
    void broadcastAfterCommit();
}

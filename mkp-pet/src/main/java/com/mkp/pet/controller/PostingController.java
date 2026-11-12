package com.mkp.pet.controller;

import com.mkp.common.dto.PostingSaveDTO;
import com.mkp.common.dto.PostingVO;
import com.mkp.common.result.R;
import com.mkp.pet.service.OssService;
import com.mkp.pet.service.PostingService;
import com.mkp.pet.service.PostingSseService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/posting")
public class PostingController {

    private final PostingService postingService;
    private final PostingSseService postingSseService;
    private final OssService ossService;

    public PostingController(PostingService postingService, PostingSseService postingSseService,
                             OssService ossService) {
        this.postingService = postingService;
        this.postingSseService = postingSseService;
        this.ossService = ossService;
    }

    private Long getUserId(HttpServletRequest request) {
        String header = request.getHeader("X-User-Id");
        return header != null ? Long.parseLong(header) : null;
    }

    @GetMapping("/list")
    public R<List<PostingVO>> list(@RequestParam(required = false) Integer category) {
        return postingService.listOpen(category);
    }

    @GetMapping("/stream")
    public SseEmitter stream() {
        SseEmitter emitter = postingSseService.createEmitter();
        postingSseService.send(emitter, postingService.listOpenVOs());
        return emitter;
    }

    @GetMapping("/{id}")
    public R<PostingVO> detail(@PathVariable Long id) {
        return postingService.getDetail(id);
    }

    @PostMapping
    public R<PostingVO> create(@RequestBody PostingSaveDTO dto, HttpServletRequest request) {
        return postingService.createPosting(getUserId(request), dto);
    }

    @PostMapping("/{id}/off")
    public R<Void> off(@PathVariable Long id, HttpServletRequest request) {
        return postingService.offPosting(getUserId(request), id);
    }

    @PostMapping("/image/upload")
    public R<List<String>> uploadImages(@RequestParam("files") MultipartFile[] files,
                                        HttpServletRequest request) {
        Long userId = getUserId(request);
        return R.ok(ossService.uploadImages(files, userId));
    }
}

package com.mkp.pet.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface OssService {
    List<String> uploadImages(MultipartFile[] files, Long userId);
}

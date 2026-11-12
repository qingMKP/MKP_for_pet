package com.mkp.post.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.mkp.post.service.OssService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class OssServiceImpl implements OssService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp"
    );
    private static final long MAX_SIZE = 5 * 1024 * 1024;

    @Value("${oss.endpoint}")
    private String endpoint;

    @Value("${oss.bucket-name}")
    private String bucketName;

    @Value("${oss.access-key-id}")
    private String accessKeyId;

    @Value("${oss.access-key-secret}")
    private String accessKeySecret;

    private OSS ossClient;

    @PostConstruct
    public void init() {
        this.ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
    }

    @PreDestroy
    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }

    @Override
    public List<String> uploadImages(MultipartFile[] files, Long userId) {
        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files) {
            urls.add(uploadSingle(file, userId));
        }
        return urls;
    }

    private String uploadSingle(MultipartFile file, Long userId) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("仅支持 JPG、PNG、GIF、WebP、BMP 格式的图片");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("图片大小不能超过5MB");
        }

        String ext = "";
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String objectKey = "posts/" + userId + "/" + UUID.randomUUID() + ext;

        try {
            ossClient.putObject(bucketName, objectKey, file.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException("OSS上传失败", e);
        }

        return "https://" + bucketName + "." + endpoint + "/" + objectKey;
    }
}

package com.mkp.common.dto;

import java.util.List;

public class PostSaveDTO {
    private String content;
    private List<String> images;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images; }
}

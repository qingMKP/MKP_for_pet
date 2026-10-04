package com.mkp.petservice.service;

import com.mkp.common.dto.PetServiceAskDTO;
import com.mkp.petservice.client.DeepSeekClient;
import org.springframework.stereotype.Service;

@Service
public class PetAdviceService {

    private static final String SYSTEM_PROMPT =
            "你是一位资深的宠物饲养与健康管理专家，擅长根据宠物的物种、品种、年龄阶段和体重，" +
            "给出科学、实用、可落地的养护建议。请用中文回答，使用 Markdown 格式，条理清晰，避免空话套话。";

    private final DeepSeekClient deepSeekClient;

    public PetAdviceService(DeepSeekClient deepSeekClient) {
        this.deepSeekClient = deepSeekClient;
    }

    public String generateAdvice(PetServiceAskDTO dto) {
        return deepSeekClient.chat(SYSTEM_PROMPT, buildPrompt(dto));
    }

    private String buildPrompt(PetServiceAskDTO dto) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下宠物信息，给出该宠物当前阶段的养护建议：\n");
        sb.append("- 物种：").append(nvl(dto.getSpecies())).append("\n");
        sb.append("- 品种：").append(nvl(dto.getBreed())).append("\n");
        sb.append("- 月龄：").append(dto.getAgeMonth() == null ? "未知" : dto.getAgeMonth() + " 个月").append("\n");
        sb.append("- 体重：").append(dto.getWeightKg() == null ? "未知" : dto.getWeightKg() + " kg").append("\n");
        sb.append("- 性别：").append(genderText(dto.getGender())).append("\n");
        if (dto.getExtra() != null && !dto.getExtra().isBlank()) {
            sb.append("- 补充信息：").append(dto.getExtra()).append("\n");
        }
        sb.append("\n请严格按以下三部分输出（Markdown 二级标题）：\n");
        sb.append("## 一、当前阶段饮食注意点\n");
        sb.append("## 二、当前阶段运动注意点\n");
        sb.append("## 三、后续阶段注意事项\n");
        sb.append("（第三部分请涵盖疫苗、驱虫、绝育、体检等后续养护要点）\n");
        return sb.toString();
    }

    private String nvl(String v) {
        return v == null || v.isBlank() ? "未知" : v;
    }

    private String genderText(Integer gender) {
        if (gender == null) return "未知";
        switch (gender) {
            case 1: return "公";
            case 2: return "母";
            default: return "未知";
        }
    }
}

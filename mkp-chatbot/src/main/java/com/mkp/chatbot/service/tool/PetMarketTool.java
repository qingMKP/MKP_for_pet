package com.mkp.chatbot.service.tool;

import com.mkp.api.PostingFeignClient;
import com.mkp.chatbot.service.react.ReActContext;
import com.mkp.chatbot.service.react.Tool;
import com.mkp.common.constant.TradeConstants;
import com.mkp.common.dto.PostingVO;
import com.mkp.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 交易广场帖子检索工具
 *
 * 功能：检索交易广场的「寄养」需求或「购买」（出售宠物）帖子，
 * 将匹配的帖子写入上下文供前端展示卡片。
 */
@Component
@Slf4j
public class PetMarketTool implements Tool {

    @Autowired
    private PostingFeignClient postingFeignClient;

    @Override
    public String getName() {
        return "search_market_posts";
    }

    @Override
    public String getDescription() {
        return "搜索交易广场的宠物帖子。当用户想找人寄养宠物（找寄养需求）或购买宠物（找出售宠物）时使用。" +
               "category=2 表示寄养需求，category=3 表示购买（出售宠物）。";
    }

    @Override
    public Map<String, String> getParametersSchema() {
        Map<String, String> schema = new HashMap<>();
        schema.put("category", "帖子分类：2=寄养需求，3=购买（出售宠物）");
        schema.put("keyword", "可选，物种或品种关键词，如'猫'、'金毛'、'布偶猫'，用于粗筛帖子");
        return schema;
    }

    @Override
    public String execute(Map<String, Object> parameters, ReActContext context) {
        Integer category = parseCategory(parameters.get("category"));
        String keyword = parameters.get("keyword") != null
                ? parameters.get("keyword").toString().trim() : "";

        log.info("执行交易广场搜索: category={}, keyword={}", category, keyword);

        try {
            R<List<PostingVO>> result = postingFeignClient.listOpen(category);

            if (result.getCode() != 200 || result.getData() == null || result.getData().isEmpty()) {
                return "交易广场暂无" + categoryName(category) + "帖子。";
            }

            List<PostingVO> postings = result.getData();

            // 关键词粗筛（宠物名/物种/品种），精确语义匹配交给 LLM
            if (!keyword.isBlank()) {
                List<PostingVO> filtered = new ArrayList<>();
                for (PostingVO p : postings) {
                    if (containsIgnoreCase(p.getPetName(), keyword)
                            || containsIgnoreCase(p.getSpecies(), keyword)
                            || containsIgnoreCase(p.getBreed(), keyword)) {
                        filtered.add(p);
                    }
                }
                postings = filtered;
            }

            if (postings.isEmpty()) {
                return "交易广场没有找到与 '" + keyword + "' 相关的" + categoryName(category) + "帖子。";
            }

            context.addRelatedPostings(postings);

            StringBuilder sb = new StringBuilder();
            sb.append("找到 ").append(postings.size()).append(" 条")
                    .append(categoryName(category)).append("帖子：\n\n");
            for (int i = 0; i < postings.size(); i++) {
                PostingVO p = postings.get(i);
                sb.append(i + 1).append(". 宠物名：").append(nvl(p.getPetName()))
                        .append("，物种：").append(nvl(p.getSpecies()))
                        .append("，品种：").append(nvl(p.getBreed()))
                        .append("，价格：").append(priceText(p))
                        .append("，描述：").append(nvl(p.getDescription()))
                        .append("\n");
            }
            return sb.toString();

        } catch (Exception e) {
            log.error("交易广场搜索失败", e);
            return "交易广场搜索失败: " + e.getMessage();
        }
    }

    private Integer parseCategory(Object raw) {
        if (raw == null) return TradeConstants.CATEGORY_FOSTER;
        try {
            int c = Integer.parseInt(raw.toString());
            return (c == TradeConstants.CATEGORY_FOSTER || c == TradeConstants.CATEGORY_SALE)
                    ? c : TradeConstants.CATEGORY_FOSTER;
        } catch (NumberFormatException e) {
            return TradeConstants.CATEGORY_FOSTER;
        }
    }

    private String categoryName(int category) {
        return category == TradeConstants.CATEGORY_SALE ? "购买" : "寄养";
    }

    private String priceText(PostingVO p) {
        if (p.getPrice() == null) return "未定价";
        String unit = "";
        if (p.getPriceUnit() != null) {
            switch (p.getPriceUnit()) {
                case TradeConstants.PRICE_UNIT_DAY: unit = "/天"; break;
                case TradeConstants.PRICE_UNIT_HOUR: unit = "/小时"; break;
                case TradeConstants.PRICE_UNIT_MINUTE: unit = "/分钟"; break;
                default: unit = ""; break;
            }
        }
        return "¥" + p.getPrice() + unit;
    }

    private boolean containsIgnoreCase(String text, String keyword) {
        return text != null && text.toLowerCase().contains(keyword.toLowerCase());
    }

    private String nvl(String s) {
        return s == null || s.isBlank() ? "无" : s;
    }
}

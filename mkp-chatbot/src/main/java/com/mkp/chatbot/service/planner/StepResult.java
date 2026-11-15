package com.mkp.chatbot.service.planner;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单个子任务的产出：给用户看的简短结论 + 给后续步骤用的结构化事实。
 *
 * 执行器被要求只输出 JSON；解析失败时退化为"整段文本当结论、没有事实"，
 * 保证任何情况下都不会因为格式问题中断链路。
 */
public record StepResult(String answer, Map<String, String> facts) {

    public static StepResult empty() {
        return new StepResult("", Map.of());
    }

    public static StepResult fromRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return empty();
        }
        String text = raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return new StepResult(text, Map.of());
        }
        try {
            JSONObject obj = JSONUtil.parseObj(text.substring(start, end + 1));
            Map<String, String> facts = new LinkedHashMap<>();
            Object rawFacts = obj.get("facts");
            if (rawFacts instanceof JSONObject factObj) {
                for (String key : factObj.keySet()) {
                    String value = factObj.getStr(key);
                    if (value != null && !value.isBlank()) {
                        facts.put(key, value);
                    }
                }
            }
            String answer = obj.getStr("answer");
            if (answer == null || answer.isBlank()) {
                return facts.isEmpty() ? new StepResult(text, facts) : new StepResult("", facts);
            }
            return new StepResult(answer.trim(), facts);
        } catch (Exception ignored) {
            return new StepResult(text, Map.of());
        }
    }
}
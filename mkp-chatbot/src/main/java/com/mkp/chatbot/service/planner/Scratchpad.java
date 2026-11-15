package com.mkp.chatbot.service.planner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多步执行期的共享区（Scratchpad）。
 *
 * 只存结构化事实和简短轨迹，不存整段工具返回，避免上下文膨胀和步骤间串味。
 * 生命周期与一次请求相同，不落库。
 */
public class Scratchpad {

    private static final int MAX_FACTS = 8;
    private static final int MAX_KEY_LEN = 20;
    private static final int MAX_VALUE_LEN = 60;
    private static final int MAX_TRACE = 6;

    private final Map<String, String> facts = new LinkedHashMap<>();
    private final List<String> traces = new ArrayList<>();

    /**
     * 合并一步产出的事实：去重、截断、上限保护。后写覆盖同名前值。
     */
    public void merge(Map<String, String> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : incoming.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim();
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (key.isEmpty() || value.isEmpty()) {
                continue;
            }
            if (key.length() > MAX_KEY_LEN) {
                key = key.substring(0, MAX_KEY_LEN);
            }
            if (value.length() > MAX_VALUE_LEN) {
                value = value.substring(0, MAX_VALUE_LEN) + "…";
            }
            if (!facts.containsKey(key) && facts.size() >= MAX_FACTS) {
                continue;
            }
            facts.put(key, value);
        }
    }

    /**
     * 记录一步的简短结论，只用于排查，不进模型上下文。
     */
    public void addTrace(String label, String answer) {
        if (answer == null || answer.isBlank()) {
            return;
        }
        if (traces.size() >= MAX_TRACE) {
            traces.remove(0);
        }
        String text = answer.trim();
        traces.add(label + "：" + (text.length() > MAX_VALUE_LEN ? text.substring(0, MAX_VALUE_LEN) + "…" : text));
    }

    /**
     * 给后续步骤的提示块。没有事实时返回空串，调用方按"无状态"处理。
     */
    public String toPromptBlock() {
        if (facts.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("已知信息（来自前面的步骤，仅供检索参考，不要复述）：\n");
        facts.forEach((key, value) -> sb.append("- ").append(key).append("：").append(value).append("\n"));
        return sb.toString();
    }

    public Map<String, String> getFacts() {
        return facts;
    }

    public List<String> getTraces() {
        return traces;
    }

    public boolean isEmpty() {
        return facts.isEmpty();
    }
}
package com.mkp.chatbot.service.react;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册中心
 *
 * 设计目标：
 * 1. 自动发现所有 Tool 实现类
 * 2. 支持动态注册和获取工具
 * 3. 新增工具零代码侵入核心逻辑
 *
 * 使用方式：
 * 1. 实现 Tool 接口
 * 2. 添加 @Component 注解
 * 3. Spring 自动扫描并注册
 */
@Component
@Slf4j
public class ToolRegistry {

    /**
     * 工具存储
     * key: 工具名称
     * value: 工具实例
     */
    private final Map<String, Tool> tools = new ConcurrentHashMap<>();

    /**
     * 构造函数注入所有 Tool Bean
     * Spring 会自动收集所有实现了 Tool 接口的 @Component
     */
    @Autowired
    public ToolRegistry(List<Tool> toolList) {
        for (Tool tool : toolList) {
            register(tool);
        }
    }

    /**
     * 注册工具
     */
    public void register(Tool tool) {
        String name = tool.getName();
        if (tools.containsKey(name)) {
            log.warn("工具 {} 已存在，将被覆盖", name);
        }
        tools.put(name, tool);
        log.info("注册工具: {} - {}", name, tool.getDescription());
    }

    /**
     * 获取工具
     *
     * @throws ToolNotFoundException 工具不存在时抛出
     */
    public Tool getTool(String name) {
        Tool tool = tools.get(name);
        if (tool == null) {
            throw new ToolNotFoundException(
                String.format("未找到工具: %s。可用工具: %s",
                    name, getAvailableToolNames())
            );
        }
        return tool;
    }

    /**
     * 获取所有工具
     */
    public List<Tool> getAllTools() {
        return new ArrayList<>(tools.values());
    }

    /**
     * 获取所有工具名称
     */
    public List<String> getAvailableToolNames() {
        return new ArrayList<>(tools.keySet());
    }

    /**
     * 检查工具是否存在
     */
    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    /**
     * 获取工具数量
     */
    public int size() {
        return tools.size();
    }
}

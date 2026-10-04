package com.mkp.chatbot.service.react;

import java.util.Map;

/**
 * 工具接口
 *
 * 所有可调用工具必须实现此接口
 *
 * 实现步骤：
 * 1. 创建类实现 Tool 接口
 * 2. 添加 @Component 注解
 * 3. 实现 execute 方法（业务逻辑）
 * 4. 启动后自动注册到 ToolRegistry
 */
public interface Tool {

    /**
     * 工具名称
     * 用于 LLM 识别和调用
     * 建议：英文小写 + 下划线，如 "search_knowledge_base"
     */
    String getName();

    /**
     * 工具描述
     * 告诉 LLM 这个工具是做什么的
     * 描述要清晰、具体，帮助 LLM 正确选择工具
     */
    String getDescription();

    /**
     * 参数 schema
     * 描述工具需要的参数（用于 LLM 生成正确参数）
     *
     * 返回格式示例：
     * {
     *   "query": "搜索关键词，描述你要查找的内容",
     *   "limit": "返回结果数量，默认5条，最大20条"
     * }
     */
    Map<String, String> getParametersSchema();

    /**
     * 执行工具
     *
     * @param parameters LLM 生成的参数
     * @param context    推理上下文（可写入辅助信息）
     * @return 工具执行结果（将作为 Observation 返回给 LLM）
     */
    String execute(Map<String, Object> parameters, ReActContext context);
}

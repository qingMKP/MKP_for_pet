package com.mkp.chatbot.service.tool;

import com.mkp.chatbot.service.react.ReActContext;
import com.mkp.chatbot.service.react.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring AI 工具声明。
 *
 * 这里只做参数适配，实际执行仍委托给原有的 Tool 实现（KnowledgeBaseTool / CommunityPostTool / PetMarketTool），
 * 这样工具逻辑与注册中心保持不变，只把 ReAct 文本协议换成模型原生 function calling。
 */
@Slf4j
@Component
public class ChatbotTools {

    private static final String CONTEXT_KEY = "mkpContext";

    private final ToolRegistry toolRegistry;

    public ChatbotTools(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @Tool(name = "search_knowledge",
          description = "搜索宠物知识库，获取专业的宠物养护、疾病、喂养知识。当用户询问宠物健康、饮食、行为、护理等专业问题时使用。")
    public String searchKnowledge(
            @ToolParam(description = "搜索关键词，描述用户问题的核心内容，如“幼犬喂食”、“猫咪驱虫”") String keyword,
            ToolContext toolContext) {
        return execute("search_knowledge", Map.of("keyword", keyword), toolContext);
    }

    @Tool(name = "search_posts",
          description = "搜索社区帖子，获取其他宠物主人的经验分享和讨论。当用户想了解真实案例、经验分享、社区讨论时使用。")
    public String searchPosts(
            @ToolParam(description = "搜索关键词，描述要查找的帖子内容") String keyword,
            ToolContext toolContext) {
        return execute("search_posts", Map.of("keyword", keyword), toolContext);
    }

    @Tool(name = "search_market_posts",
          description = "搜索交易广场的宠物帖子。当用户想找人寄养宠物（找寄养需求）或购买宠物（找出售宠物）时使用。")
    public String searchMarketPosts(
            @ToolParam(description = "帖子分类：2=寄养需求，3=购买（出售宠物）") Integer category,
            @ToolParam(description = "可选，物种或品种关键词，如“猫”、“金毛”、“布偶猫”", required = false) String keyword,
            ToolContext toolContext) {
        Map<String, Object> params = new HashMap<>();
        params.put("category", category);
        if (keyword != null) {
            params.put("keyword", keyword);
        }
        return execute("search_market_posts", params, toolContext);
    }

    private String execute(String toolName, Map<String, Object> params, ToolContext toolContext) {
        ReActContext context = resolveContext(toolContext);
        try {
            return toolRegistry.getTool(toolName).execute(params, context);
        } catch (Exception e) {
            log.error("工具执行失败: {}", toolName, e);
            return "工具执行失败: " + e.getMessage();
        }
    }

    private ReActContext resolveContext(ToolContext toolContext) {
        if (toolContext != null && toolContext.getContext() != null) {
            Object value = toolContext.getContext().get(CONTEXT_KEY);
            if (value instanceof ReActContext) {
                return (ReActContext) value;
            }
        }
        return new ReActContext();
    }
}
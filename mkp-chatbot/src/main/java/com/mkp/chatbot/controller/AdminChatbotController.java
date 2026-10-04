package com.mkp.chatbot.controller;

import com.mkp.chatbot.service.ChatbotService;
import com.mkp.chatbot.service.vector.KnowledgeVectorSyncService;
import com.mkp.common.dto.KnowledgeSaveDTO;
import com.mkp.common.entity.ChatbotKnowledge;
import com.mkp.common.result.R;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/chatbot/knowledge")
public class AdminChatbotController {

    private final ChatbotService chatbotService;
    private final KnowledgeVectorSyncService vectorSyncService;

    public AdminChatbotController(ChatbotService chatbotService,
                                  KnowledgeVectorSyncService vectorSyncService) {
        this.chatbotService = chatbotService;
        this.vectorSyncService = vectorSyncService;
    }

    @GetMapping("/list")
    public R<List<ChatbotKnowledge>> listKnowledge() {
        return chatbotService.listKnowledge();
    }

    @PostMapping
    public R<Void> addKnowledge(@RequestBody KnowledgeSaveDTO dto) {
        return chatbotService.addKnowledge(dto);
    }

    @PutMapping("/{id}")
    public R<Void> updateKnowledge(@PathVariable Long id, @RequestBody KnowledgeSaveDTO dto) {
        return chatbotService.updateKnowledge(id, dto);
    }

    @DeleteMapping("/{id}")
    public R<Void> deleteKnowledge(@PathVariable Long id) {
        return chatbotService.deleteKnowledge(id);
    }

    @DeleteMapping("/batch")
    public R<Void> batchDeleteKnowledge(@RequestBody List<Long> ids) {
        return chatbotService.batchDeleteKnowledge(ids);
    }

    /**
     * 手动触发全量向量同步（重建 Redis 向量索引）
     *
     * 使用场景：
     * - 首次部署后初始化向量
     * - Embedding 模型 / 维度变更后重建
     * - 数据迁移后重新向量化
     */
    @PostMapping("/sync-vectors")
    public R<String> syncVectors() {
        vectorSyncService.syncAll();
        return R.ok("向量同步已触发，请稍后查询知识库验证效果");
    }
}

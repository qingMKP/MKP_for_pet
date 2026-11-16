package com.mkp.chatbot.controller;

import com.mkp.chatbot.service.ChatbotService;
import com.mkp.chatbot.service.doc.KnowledgeDocumentService;
import com.mkp.chatbot.service.vector.KnowledgeVectorSyncService;
import com.mkp.common.dto.DocumentIngestDTO;
import com.mkp.common.dto.KnowledgeSaveDTO;
import com.mkp.common.entity.ChatbotDocument;
import com.mkp.common.entity.ChatbotKnowledge;
import com.mkp.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Slf4j
@RestController
@RequestMapping("/api/admin/chatbot/knowledge")
public class AdminChatbotController {

    private final ChatbotService chatbotService;
    private final KnowledgeVectorSyncService vectorSyncService;
    private final KnowledgeDocumentService documentService;

    public AdminChatbotController(ChatbotService chatbotService,
                                  KnowledgeVectorSyncService vectorSyncService,
                                  KnowledgeDocumentService documentService) {
        this.chatbotService = chatbotService;
        this.vectorSyncService = vectorSyncService;
        this.documentService = documentService;
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

    // ==================== 长文档（父子块） ====================

    /**
     * 粘贴文本入库：自动切父块/子块并向量化。
     */
    @PostMapping("/document")
    public R<ChatbotDocument> addDocument(@RequestBody DocumentIngestDTO dto) {
        try {
            ChatbotDocument doc = documentService.ingest(
                    dto.getTitle(), dto.getCategory(), "text", null, dto.getContent());
            return R.ok(doc);
        } catch (Exception e) {
            log.error("文档入库失败", e);
            return R.fail("文档入库失败: " + e.getMessage());
        }
    }

    /**
     * 上传文件入库：支持 .md / .markdown / .txt（UTF-8）。
     * PDF / DOCX 需要额外解析库，当前不接。
     */
    @PostMapping("/document/upload")
    public R<ChatbotDocument> uploadDocument(@RequestParam("file") MultipartFile file,
                                             @RequestParam(value = "title", required = false) String title,
                                             @RequestParam(value = "category", required = false) String category) {
        if (file == null || file.isEmpty()) {
            return R.fail("请选择要上传的文件");
        }
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".md") && !lower.endsWith(".markdown") && !lower.endsWith(".txt")) {
            return R.fail("目前仅支持 .md / .markdown / .txt 文件，PDF、DOCX 需要额外解析库");
        }
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            String docTitle = (title == null || title.isBlank())
                    ? fileName.substring(0, fileName.lastIndexOf('.'))
                    : title;
            ChatbotDocument doc = documentService.ingest(
                    docTitle, category, "file", fileName, content);
            return R.ok(doc);
        } catch (Exception e) {
            log.error("文件入库失败: {}", fileName, e);
            return R.fail("文件入库失败: " + e.getMessage());
        }
    }

    @GetMapping("/document/list")
    public R<List<ChatbotDocument>> listDocuments() {
        return R.ok(documentService.list());
    }

    @DeleteMapping("/document/{id}")
    public R<Void> deleteDocument(@PathVariable Long id) {
        try {
            documentService.delete(id);
            return R.ok();
        } catch (Exception e) {
            log.error("删除文档失败: id={}", id, e);
            return R.fail("删除文档失败: " + e.getMessage());
        }
    }

    /**
     * 重建全部子块向量。
     * 使用场景：换 embedding 模型 / 维度、Redis 被清空、数据是直接 SQL 灌进来的。
     */
    @PostMapping("/document/sync-vectors")
    public R<String> syncDocumentVectors() {
        try {
            int count = documentService.syncAllVectors();
            return R.ok("长文档向量同步完成，共写入 " + count + " 条子块向量");
        } catch (Exception e) {
            log.error("长文档向量同步失败", e);
            return R.fail("长文档向量同步失败: " + e.getMessage());
        }
    }
}

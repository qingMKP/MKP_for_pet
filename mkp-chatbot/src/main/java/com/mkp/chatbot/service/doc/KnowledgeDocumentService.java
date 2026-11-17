package com.mkp.chatbot.service.doc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mkp.chatbot.mapper.ChatbotChunkMapper;
import com.mkp.chatbot.mapper.ChatbotDocumentMapper;
import com.mkp.chatbot.service.vector.ChunkVectorStore;
import com.mkp.chatbot.service.vector.EmbeddingService;
import com.mkp.common.entity.ChatbotChunk;
import com.mkp.common.entity.ChatbotDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 长文档入库编排。
 *
 * 一次入库 = 建文档 → 切父块/子块 → 子块算向量写 Milvus → 回写状态。
 * 只有子块进向量库，父块只落 MySQL，命中后按 parentId 回查。
 */
@Slf4j
@Service
public class KnowledgeDocumentService {

    private final ChatbotDocumentMapper documentMapper;
    private final ChatbotChunkMapper chunkMapper;
    private final DocumentChunker chunker;
    private final ChunkVectorStore chunkVectorStore;
    private final EmbeddingService embeddingService;

    @Value("${mkp.knowledge.vector.enabled:true}")
    private boolean vectorEnabled;

    public KnowledgeDocumentService(ChatbotDocumentMapper documentMapper,
                                    ChatbotChunkMapper chunkMapper,
                                    DocumentChunker chunker,
                                    ChunkVectorStore chunkVectorStore,
                                    EmbeddingService embeddingService) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.chunker = chunker;
        this.chunkVectorStore = chunkVectorStore;
        this.embeddingService = embeddingService;
    }

    /**
     * 入库一篇长文档：建文档记录 → 切父块/子块落 MySQL → 子块向量写 Milvus → 回写文档状态。
     *
     * @param title      文档标题；为空时按“未命名文档”入库
     * @param category   文档分类（feeding/care/health/behavior/general），冗余存进 Milvus 便于排查
     * @param sourceType 来源类型：text=手工粘贴，file=上传文件；为空按 text 处理
     * @param fileName   原始文件名，仅上传文件时有值；手工粘贴传 null
     * @param rawText    文档正文（Markdown / 纯文本均可），为空直接抛异常
     * @return 回写完状态和子块数量的文档实体，id 为 MySQL 自增主键
     * @throws IllegalArgumentException 正文为空，或切块结果为空
     */
    public ChatbotDocument ingest(String title, String category, String sourceType,
                                  String fileName, String rawText) {
        if (rawText == null || rawText.isBlank()) {
            throw new IllegalArgumentException("文档内容为空");
        }
        String docTitle = (title == null || title.isBlank()) ? "未命名文档" : title.trim();

        ChatbotDocument doc = new ChatbotDocument();
        doc.setTitle(docTitle);
        doc.setCategory(category);
        doc.setSourceType(sourceType == null ? "text" : sourceType);
        doc.setFileName(fileName);
        doc.setCharCount(rawText.length());
        doc.setChunkCount(0);
        doc.setStatus(0);
        documentMapper.insert(doc);

        try {
            List<DocumentChunker.ParentBlock> blocks = chunker.split(rawText);
            if (blocks.isEmpty()) {
                throw new IllegalArgumentException("切块结果为空，请检查文档内容");
            }
            List<ChatbotChunk> children = saveChunks(doc.getId(), blocks);
            log.info("文档切块完成: docId={} title={} 父块={} 子块={}",
                    doc.getId(), docTitle, blocks.size(), children.size());

            if (vectorEnabled) {
                writeVectors(docTitle, category, children);
                doc.setStatus(1);
            } else {
                doc.setStatus(0);
            }
            doc.setChunkCount(children.size());
            documentMapper.updateById(doc);
            return doc;
        } catch (Exception e) {
            log.error("文档入库失败: docId={} title={}", doc.getId(), docTitle, e);
            doc.setStatus(2);
            documentMapper.updateById(doc);
            throw e;
        }
    }

    /**
     * 把切块结果落 MySQL：每个父块插一行（parent_id=0），父块下的每个子块插一行（parent_id=父块 id）。
     *
     * @param documentId 所属文档 id（mkp_chatbot_document.id）
     * @param blocks     切块器输出的父块列表，每个父块自带若干子块文本
     * @return 全部子块实体，已带 MySQL 自增 id 和 parentId，供后续向量化使用
     */
    private List<ChatbotChunk> saveChunks(Long documentId, List<DocumentChunker.ParentBlock> blocks) {
        List<ChatbotChunk> children = new ArrayList<>();
        int parentSeq = 1;
        for (DocumentChunker.ParentBlock block : blocks) {
            ChatbotChunk parent = new ChatbotChunk();
            parent.setDocumentId(documentId);
            parent.setParentId(0L);
            parent.setSeq(parentSeq++);
            parent.setSectionTitle(block.sectionTitle());
            parent.setContent(block.content());
            parent.setCharCount(block.content().length());
            chunkMapper.insert(parent);

            int childSeq = 1;
            for (String childText : block.children()) {
                ChatbotChunk child = new ChatbotChunk();
                child.setDocumentId(documentId);
                child.setParentId(parent.getId());
                child.setSeq(childSeq++);
                child.setSectionTitle(block.sectionTitle());
                child.setContent(childText);
                child.setCharCount(childText.length());
                chunkMapper.insert(child);
                children.add(child);
            }
        }
        return children;
    }

    /**
     * 子块批量向量化并写入 Milvus（collection mkp_knowledge_chunk）。
     * embedding 输入 = 文档标题 + 小节标题 + 子块正文，避免 300 字的片段脱离上下文后语义跑偏。
     *
     * @param title    文档标题，参与 embedding 文本拼接，同时作为 Milvus 冗余字段
     * @param category 文档分类，仅作为 Milvus 冗余字段
     * @param children 待向量化的子块，必须已带 id / parentId
     */
    private void writeVectors(String title, String category, List<ChatbotChunk> children) {
        if (children.isEmpty()) {
            return;
        }
        // 1. 每个子块拼一段用于 embedding 的文本（文档标题 + 小节标题 + 正文），顺序与 children 一致
        List<String> texts = new ArrayList<>(children.size());
        for (ChatbotChunk child : children) {
            texts.add(embedText(title, child));
        }
        // 2. 批量转向量：内部按 batch-size=10 分批调 DashScope，返回顺序与 texts 严格对应
        List<List<Float>> vectors = embeddingService.embedBatch(texts);
        // 3. 子块实体 + 向量装成一行，title / category 作为冗余字段一起写进向量库
        List<ChunkVectorStore.ChunkVector> rows = new ArrayList<>(children.size());
        for (int i = 0; i < children.size(); i++) {
            rows.add(new ChunkVectorStore.ChunkVector(children.get(i), title, category, vectors.get(i)));
        }
        // 4. 一次 upsert 写入 mkp_knowledge_chunk，主键是子块 id，重复执行是覆盖不是追加
        chunkVectorStore.writeBatch(rows);
    }

    /**
     * 拼一条子块参与 embedding 的文本。
     *
     * @param title 文档标题
     * @param chunk 子块，读取其中的小节标题和正文
     * @return “文档标题 + 小节标题 + 子块正文”的拼接串
     */
    private String embedText(String title, ChatbotChunk chunk) {
        StringBuilder sb = new StringBuilder();
        sb.append(title).append(' ');
        if (chunk.getSectionTitle() != null && !chunk.getSectionTitle().isBlank()) {
            sb.append(chunk.getSectionTitle()).append(' ');
        }
        sb.append(chunk.getContent());
        return sb.toString();
    }

    /**
     * 查全部长文档，按 id 倒序（最新入库的在前）。
     *
     * @return 文档列表；没有数据时返回空列表
     */
    public List<ChatbotDocument> list() {
        return documentMapper.selectList(new LambdaQueryWrapper<ChatbotDocument>()
                .orderByDesc(ChatbotDocument::getId));
    }

    /**
     * 删除一篇文档：先按子块 id 清 Milvus 向量，再逻辑删 MySQL 的切片和文档，
     * 避免向量还在、MySQL 已删导致召回脏数据。
     *
     * @param documentId 文档 id；为 null 时直接返回
     */
    @Transactional
    public void delete(Long documentId) {
        if (documentId == null) {
            return;
        }
        List<ChatbotChunk> children = chunkMapper.selectList(
                new LambdaQueryWrapper<ChatbotChunk>()
                        .eq(ChatbotChunk::getDocumentId, documentId)
                        .gt(ChatbotChunk::getParentId, 0));
        List<Long> childIds = new ArrayList<>(children.size());
        for (ChatbotChunk child : children) {
            childIds.add(child.getId());
        }
        chunkVectorStore.removeAll(childIds);

        chunkMapper.delete(new LambdaQueryWrapper<ChatbotChunk>()
                .eq(ChatbotChunk::getDocumentId, documentId));
        documentMapper.deleteById(documentId);
        log.info("文档已删除: docId={} 清理子块向量 {} 条", documentId, childIds.size());
    }

    /**
     * 重建全部文档的子块向量：逐篇查子块 → 重新算 embedding → 覆盖写 Milvus → 回写文档状态。
     * 使用场景：换 embedding 模型 / 维度、Milvus 集合被重建、历史数据是直接 SQL 灌进来的。
     *
     * @return 本次成功写入的子块向量总条数；向量检索关闭时返回 0
     */
    public int syncAllVectors() {
        if (!vectorEnabled) {
            log.warn("向量检索未启用，跳过长文档向量同步");
            return 0;
        }
        List<ChatbotDocument> documents = list();
        int total = 0;
        for (ChatbotDocument doc : documents) {
            List<ChatbotChunk> children = chunkMapper.selectList(
                    new LambdaQueryWrapper<ChatbotChunk>()
                            .eq(ChatbotChunk::getDocumentId, doc.getId())
                            .gt(ChatbotChunk::getParentId, 0)
                            .orderByAsc(ChatbotChunk::getId));
            if (children.isEmpty()) {
                continue;
            }
            try {
                writeVectors(doc.getTitle(), doc.getCategory(), children);
                ChatbotDocument update = new ChatbotDocument();
                update.setId(doc.getId());
                update.setChunkCount(children.size());
                update.setStatus(1);
                documentMapper.updateById(update);
                total += children.size();
                log.info("文档向量同步完成: docId={} title={} 子块={}",
                        doc.getId(), doc.getTitle(), children.size());
            } catch (Exception e) {
                log.error("文档向量同步失败: docId={} title={}", doc.getId(), doc.getTitle(), e);
                ChatbotDocument update = new ChatbotDocument();
                update.setId(doc.getId());
                update.setStatus(2);
                documentMapper.updateById(update);
            }
        }
        log.info("长文档向量同步结束，共写入 {} 条子块向量", total);
        return total;
    }
}

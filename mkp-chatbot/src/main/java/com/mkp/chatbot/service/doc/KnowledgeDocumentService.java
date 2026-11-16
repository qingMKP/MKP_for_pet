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
 * 一次入库 = 建文档 → 切父块/子块 → 子块算向量写 Redis → 回写状态。
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
     * 入库一篇文档。
     *
     * @param sourceType text=手工粘贴，file=上传文件
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
     * 落库父块和子块，返回全部子块（带自增 id，供向量化使用）。
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
     * 子块批量转向量并写 Redis。embedding 输入带上文档标题和小节标题，
     * 避免 200 字的片段脱离上下文后语义跑偏。
     */
    private void writeVectors(String title, String category, List<ChatbotChunk> children) {
        if (children.isEmpty()) {
            return;
        }
        List<String> texts = new ArrayList<>(children.size());
        for (ChatbotChunk child : children) {
            texts.add(embedText(title, child));
        }
        List<List<Float>> vectors = embeddingService.embedBatch(texts);
        for (int i = 0; i < children.size(); i++) {
            chunkVectorStore.write(children.get(i), title, category, vectors.get(i));
        }
    }

    private String embedText(String title, ChatbotChunk chunk) {
        StringBuilder sb = new StringBuilder();
        sb.append(title).append(' ');
        if (chunk.getSectionTitle() != null && !chunk.getSectionTitle().isBlank()) {
            sb.append(chunk.getSectionTitle()).append(' ');
        }
        sb.append(chunk.getContent());
        return sb.toString();
    }

    public List<ChatbotDocument> list() {
        return documentMapper.selectList(new LambdaQueryWrapper<ChatbotDocument>()
                .orderByDesc(ChatbotDocument::getId));
    }

    /**
     * 删除文档：先清 Redis 子块向量，再逻辑删 MySQL，避免召回到已删内容。
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
     * 重建全部子块向量。
     * 使用场景：换了 embedding 模型 / 维度、Redis 被清空、历史数据是直接 SQL 灌进来的。
     *
     * @return 成功向量化的子块数量
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

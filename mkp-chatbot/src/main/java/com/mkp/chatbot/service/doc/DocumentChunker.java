package com.mkp.chatbot.service.doc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 长文档切块器：先按标题层级切父块，父块再按字数切子块（带重叠）。
 *
 * 父块 = 一节完整内容，喂给大模型的是它；
 * 子块 = 200~400 字的小段，只用来算向量做召回，命中后再回查父块。
 */
@Slf4j
@Component
public class DocumentChunker {

    /** 子块目标字数 */
    @Value("${mkp.knowledge.vector.chunk.child-size:300}")
    private int childSize;

    /** 子块之间的重叠字数（约 20%） */
    @Value("${mkp.knowledge.vector.chunk.child-overlap:60}")
    private int childOverlap;

    /** 父块最大字数，超过就先按段落拆成多个父块 */
    @Value("${mkp.knowledge.vector.chunk.parent-max-size:1200}")
    private int parentMaxSize;

    /** 子块最小字数，太短的片段直接丢掉 */
    private static final int MIN_CHILD_SIZE = 20;

    public record ParentBlock(String sectionTitle, String content, List<String> children) {
    }

    /**
     * @param rawText 原始文档正文（Markdown / 纯文本均可）
     * @return 父块列表，每个父块带自己的子块
     */
    public List<ParentBlock> split(String rawText) {
        List<ParentBlock> blocks = new ArrayList<>();
        if (rawText == null || rawText.isBlank()) {
            return blocks;
        }

        String text = normalize(rawText);
        text = stripLeadingTitle(text);

        for (Section section : splitSections(text)) {
            for (String part : splitByParagraph(section.body(), parentMaxSize)) {
                List<String> children = splitChildren(part);
                if (children.isEmpty()) {
                    continue;
                }
                String content = section.title().isBlank()
                        ? part
                        : "## " + section.title() + "\n" + part;
                blocks.add(new ParentBlock(section.title(), content, children));
            }
        }
        return blocks;
    }

    // ==================== 清洗 ====================

    private String normalize(String raw) {
        String text = raw.replace("\r\n", "\n").replace("\r", "\n");
        StringBuilder sb = new StringBuilder(text.length());
        int blankRun = 0;
        for (String line : text.split("\n", -1)) {
            String trimmed = line.replaceAll("[ \t]+$", "").replace("\u00a0", " ");
            if (trimmed.isBlank()) {
                blankRun++;
                // 连续空行压成一个
                if (blankRun > 1) {
                    continue;
                }
                sb.append('\n');
            } else {
                blankRun = 0;
                sb.append(trimmed).append('\n');
            }
        }
        return sb.toString().trim();
    }

    /** 文档首行的「# 标题」和文档标题重复，切块时去掉 */
    private String stripLeadingTitle(String text) {
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            if (lines[i].startsWith("# ")) {
                StringBuilder sb = new StringBuilder();
                for (int j = i + 1; j < lines.length; j++) {
                    sb.append(lines[j]).append('\n');
                }
                return sb.toString().trim();
            }
            break;
        }
        return text;
    }

    // ==================== 按标题切父块 ====================

    private record Section(String title, String body) {
    }

    private List<Section> splitSections(String text) {
        List<Section> sections = new ArrayList<>();
        String currentTitle = "";
        StringBuilder body = new StringBuilder();

        for (String line : text.split("\n", -1)) {
            String title = headingTitle(line);
            if (title != null) {
                if (!body.toString().isBlank()) {
                    sections.add(new Section(currentTitle, body.toString().trim()));
                }
                currentTitle = title;
                body.setLength(0);
            } else {
                body.append(line).append('\n');
            }
        }
        if (!body.toString().isBlank()) {
            sections.add(new Section(currentTitle, body.toString().trim()));
        }
        return sections;
    }

    /** 命中 Markdown 标题返回标题文字，否则返回 null（代码块内的 # 不算） */
    private String headingTitle(String line) {
        if (!line.startsWith("#")) {
            return null;
        }
        int i = 0;
        while (i < line.length() && line.charAt(i) == '#') {
            i++;
        }
        if (i == 0 || i > 6 || i >= line.length() || line.charAt(i) != ' ') {
            return null;
        }
        return line.substring(i).trim();
    }

    /** 一节太长时按空行分段，攒到 parentMaxSize 就切一个父块 */
    private List<String> splitByParagraph(String body, int maxSize) {
        List<String> parts = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (String paragraph : body.split("\n\\s*\n")) {
            String p = paragraph.trim();
            if (p.isEmpty()) {
                continue;
            }
            if (p.length() > maxSize) {
                if (!buf.isEmpty()) {
                    parts.add(buf.toString().trim());
                    buf.setLength(0);
                }
                parts.addAll(splitChildren(p));
                continue;
            }
            if (buf.length() + p.length() > maxSize && !buf.isEmpty()) {
                parts.add(buf.toString().trim());
                buf.setLength(0);
            }
            if (!buf.isEmpty()) {
                buf.append("\n\n");
            }
            buf.append(p);
        }
        if (!buf.isEmpty()) {
            parts.add(buf.toString().trim());
        }
        return parts;
    }

    // ==================== 按字数切子块 ====================

    private List<String> splitChildren(String text) {
        List<String> children = new ArrayList<>();
        String body = text.trim();
        if (body.length() <= MIN_CHILD_SIZE) {
            return children;
        }
        if (body.length() <= childSize + childOverlap) {
            children.add(body);
            return children;
        }

        int start = 0;
        while (start < body.length()) {
            int end = Math.min(start + childSize, body.length());
            if (end < body.length()) {
                int cut = lastSentenceEnd(body, start + childSize / 2, end);
                if (cut > start) {
                    end = cut + 1;
                }
            }
            String piece = body.substring(start, end).trim();
            if (piece.length() >= MIN_CHILD_SIZE) {
                children.add(piece);
            }
            if (end >= body.length()) {
                break;
            }
            start = Math.max(end - childOverlap, start + 1);
        }
        return children;
    }

    /** 在 [from, to) 里找最后一个句末标点，尽量不把一个句子切断 */
    private int lastSentenceEnd(String text, int from, int to) {
        for (int i = to - 1; i >= from; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '；' || c == '\n'
                    || c == '.' || c == '!' || c == '?') {
                return i;
            }
        }
        return -1;
    }
}

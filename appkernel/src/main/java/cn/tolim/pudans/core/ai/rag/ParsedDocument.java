package cn.tolim.pudans.core.ai.rag;

import java.util.Comparator;
import java.util.List;

public record ParsedDocument(String text, List<Heading> headings, List<Page> pages) {

    public ParsedDocument {
        text = text == null ? "" : text;
        headings = headings == null ? List.of() : headings.stream()
                .sorted(Comparator.comparingInt(Heading::offset))
                .toList();
        pages = pages == null ? List.of() : pages.stream()
                .sorted(Comparator.comparingInt(Page::offset))
                .toList();
        for (var heading : headings) {
            validateOffset(heading.offset(), text.length());
            if (heading.level() < 1) {
                throw new IllegalArgumentException("标题层级必须大于 0。");
            }
            if (heading.title() == null || heading.title().isBlank()) {
                throw new IllegalArgumentException("标题内容不能为空。");
            }
        }
        for (var page : pages) {
            validateOffset(page.offset(), text.length());
            if (page.pageNo() < 1) {
                throw new IllegalArgumentException("页码必须大于 0。");
            }
        }
    }

    public static ParsedDocument of(String text) {
        return new ParsedDocument(text, List.of(), List.of());
    }

    private static void validateOffset(int offset, int textLength) {
        if (offset < 0 || offset > textLength) {
            throw new IllegalArgumentException("结构偏移必须落在文本范围内。");
        }
    }

    public record Heading(int offset, int level, String title) {
    }

    public record Page(int offset, int pageNo) {
    }
}

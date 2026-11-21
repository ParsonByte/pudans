package cn.tolim.pudans.core.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.tolim.pudans.contract.ai.resource.KnowledgeBase;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkingTest {

    private static final String HEADED_TEXT = String.join(
            "\n",
            "第一章 概述",
            "这是第一章的内容。",
            "",
            "1.1 背景",
            "这是背景内容。",
            "",
            "1.2 目标",
            "这是目标内容。",
            "",
            "第二章 安装",
            "安装步骤说明。");

    private final Chunking service = new Chunking();

    @Test
    void splitsRecursivelyByHeadingHierarchy() {
        var headings = List.of(
                new ParsedDocument.Heading(HEADED_TEXT.indexOf("第一章 概述"), 1, "第一章 概述"),
                new ParsedDocument.Heading(HEADED_TEXT.indexOf("1.1 背景"), 2, "1.1 背景"),
                new ParsedDocument.Heading(HEADED_TEXT.indexOf("1.2 目标"), 2, "1.2 目标"),
                new ParsedDocument.Heading(HEADED_TEXT.indexOf("第二章 安装"), 1, "第二章 安装"));
        var document = new ParsedDocument(HEADED_TEXT, headings, List.of());

        var chunks = service.chunk(document, KnowledgeBase.ChunkStrategy.RECURSIVE, 1000, 0);

        assertThat(chunks).hasSize(4);
        assertThat(chunks).extracting(KnowledgeBaseChunk::getChunkIndex).containsExactly(0, 1, 2, 3);
        assertThat(chunks).extracting(KnowledgeBaseChunk::getHeadingPath)
                .containsExactly(
                        "第一章 概述",
                        "第一章 概述 > 1.1 背景",
                        "第一章 概述 > 1.2 目标",
                        "第二章 安装");
        assertThat(chunks.get(0).getContent()).isEqualTo("第一章 概述\n这是第一章的内容。");
        assertThat(chunks.get(3).getContent()).isEqualTo("第二章 安装\n安装步骤说明。");
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.getTokenCount()).isPositive();
            assertThat(chunk.getPageNo()).isNull();
        });
    }

    @Test
    void keepsPreambleBeforeTheFirstHeading() {
        var content = "前言内容。\n第一章\n正文内容。";
        var document = new ParsedDocument(
                content,
                List.of(new ParsedDocument.Heading(content.indexOf("第一章"), 1, "第一章")),
                List.of());

        var chunks = service.chunk(document, KnowledgeBase.ChunkStrategy.RECURSIVE, 100, 0);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getContent()).isEqualTo("前言内容。");
        assertThat(chunks.get(0).getHeadingPath()).isNull();
        assertThat(chunks.get(1).getHeadingPath()).isEqualTo("第一章");
    }

    @Test
    void splitsSectionsOverTheChunkSizeAtSentenceBoundaries() {
        var content = "第一段内容。第二段内容。第三段内容。第四段内容。";
        var document = ParsedDocument.of(content);

        var chunks = service.chunk(document, KnowledgeBase.ChunkStrategy.RECURSIVE, 10, 3);

        assertThat(chunks).hasSize(4);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.getContent().length()).isLessThanOrEqualTo(10));
        assertThat(chunks.get(0).getContent()).isEqualTo("第一段内容。");
        assertThat(chunks.get(1).getContent()).startsWith("内容。");
        assertThat(chunks.get(0).getContent()).endsWith("内容。");
    }

    @Test
    void splitsFixedWindowsWithOverlap() {
        var content = "0123456789abcdefghijklmnopqrstuvwxyz";
        var document = ParsedDocument.of(content);

        var chunks = service.chunk(document, KnowledgeBase.ChunkStrategy.FIXED, 10, 3);

        assertThat(chunks).extracting(KnowledgeBaseChunk::getContent)
                .containsExactly("0123456789", "789abcdefg", "efghijklmn", "lmnopqrstu", "stuvwxyz");
        assertThat(chunks).extracting(KnowledgeBaseChunk::getChunkIndex).containsExactly(0, 1, 2, 3, 4);
        assertThat(chunks.get(0).getContent()).endsWith("789");
        assertThat(chunks.get(1).getContent()).startsWith("789");
    }

    @Test
    void tracksPageNumbersForFixedChunks() {
        var pages = List.of(new ParsedDocument.Page(0, 1), new ParsedDocument.Page(5, 2));
        var document = new ParsedDocument("abcdefghij", List.of(), pages);

        var chunks = service.chunk(document, KnowledgeBase.ChunkStrategy.FIXED, 5, 0);

        assertThat(chunks).extracting(KnowledgeBaseChunk::getPageNo).containsExactly(1, 2);
    }

    @Test
    void estimatesTokenCountForChineseAndEnglishText() {
        var chinese = service.chunk(ParsedDocument.of("你好世界"), KnowledgeBase.ChunkStrategy.FIXED, 100, 0);
        var english = service.chunk(ParsedDocument.of("hello world"), KnowledgeBase.ChunkStrategy.FIXED, 100, 0);

        assertThat(chinese).singleElement().extracting(KnowledgeBaseChunk::getTokenCount).isEqualTo(4);
        assertThat(english).singleElement().extracting(KnowledgeBaseChunk::getTokenCount).isEqualTo(2);
    }

    @Test
    void returnsNoChunksForBlankText() {
        assertThat(service.chunk(ParsedDocument.of(" \n\t"), KnowledgeBase.ChunkStrategy.RECURSIVE, 10, 2)).isEmpty();
        assertThat(service.chunk(ParsedDocument.of(""), KnowledgeBase.ChunkStrategy.FIXED, 10, 2)).isEmpty();
    }

    @Test
    void rejectsOverlapThatIsNotSmallerThanChunkSize() {
        assertThatThrownBy(() -> service.chunk(ParsedDocument.of("内容"), KnowledgeBase.ChunkStrategy.FIXED, 10, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.chunk(ParsedDocument.of("内容"), KnowledgeBase.ChunkStrategy.FIXED, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnsupportedSemanticStrategy() {
        assertThatThrownBy(() -> service.chunk(
                        ParsedDocument.of("内容"), KnowledgeBase.ChunkStrategy.SEMANTIC, 10, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsStructureOffsetsOutsideTheText() {
        assertThatThrownBy(() -> new ParsedDocument(
                        "short",
                        List.of(new ParsedDocument.Heading(9, 1, "标题")),
                        List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ParsedDocument(
                        "short", List.of(), List.of(new ParsedDocument.Page(0, 0))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

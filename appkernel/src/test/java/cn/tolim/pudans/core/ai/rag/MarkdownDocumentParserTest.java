package cn.tolim.pudans.core.ai.rag;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MarkdownDocumentParserTest {

    private final MarkdownDocumentParser parser = new MarkdownDocumentParser();

    @Test
    void supportsMarkdownMimeTypesAndExtensions() {
        assertThat(parser.supports("guide.MD", null)).isTrue();
        assertThat(parser.supports("guide.markdown", null)).isTrue();
        assertThat(parser.supports("guide", "text/markdown")).isTrue();
        assertThat(parser.supports("guide.txt", "text/plain")).isFalse();
    }

    @Test
    void extractsAtxHeadingsWithOffsetsAndLevels() {
        var markdown = "# 第一章\n内容\n\n## 1.1 背景\n正文\n# 第二章\n";
        var document = parser.parse(markdown.getBytes(UTF_8));

        assertThat(document.text()).isEqualTo(markdown);
        assertThat(document.headings())
                .containsExactly(
                        new ParsedDocument.Heading(markdown.indexOf("# 第一章"), 1, "第一章"),
                        new ParsedDocument.Heading(markdown.indexOf("## 1.1 背景"), 2, "1.1 背景"),
                        new ParsedDocument.Heading(markdown.indexOf("# 第二章"), 1, "第二章"));
    }

    @Test
    void ignoresHeadingsInsideFencedCodeBlocks() {
        var markdown = "# 标题\n```\n# 代码里的井号\n```\n## 子标题\n";
        var document = parser.parse(markdown.getBytes(UTF_8));

        assertThat(document.headings()).extracting(ParsedDocument.Heading::title)
                .containsExactly("标题", "子标题");
    }

    @Test
    void handlesCrlfLineEndings() {
        var markdown = "## 标题\r\n正文\r\n";
        var document = parser.parse(markdown.getBytes(UTF_8));

        assertThat(document.headings()).containsExactly(new ParsedDocument.Heading(0, 2, "标题"));
    }

    @Test
    void returnsEmptyStructureForEmptyContent() {
        var document = parser.parse(new byte[0]);

        assertThat(document.text()).isEmpty();
        assertThat(document.headings()).isEmpty();
    }
}

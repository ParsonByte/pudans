package cn.tolim.pudans.core.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentParserResolverTest {

    private final DocumentParserResolver resolver = resolver();

    @Test
    void prefersDedicatedReadersOverTheTikaFallback() {
        assertThat(resolver.resolve("manual.pdf", null)).isInstanceOf(PdfDocumentParser.class);
        assertThat(resolver.resolve("manual.docx", null)).isInstanceOf(WordDocumentParser.class);
        assertThat(resolver.resolve("photo.png", "image/png")).isInstanceOf(ImageDocumentParser.class);
        assertThat(resolver.resolve("manual.txt", "text/plain")).isInstanceOf(TxtDocumentParser.class);
        assertThat(resolver.resolve("manual.md", null)).isInstanceOf(MarkdownDocumentParser.class);
        assertThat(resolver.resolve("page.html", "text/html")).isInstanceOf(TikaDocumentParser.class);
    }

    @Test
    void rejectsUnsupportedDocuments() {
        assertThatThrownBy(() -> new DocumentParserResolver(List.of()).resolve("archive.bin", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的文档类型");
    }

    private static DocumentParserResolver resolver() {
        return new DocumentParserResolver(List.of(
                new TikaDocumentParser(),
                new ImageDocumentParser(),
                new WordDocumentParser(),
                new PdfDocumentParser(),
                new MarkdownDocumentParser(),
                new TxtDocumentParser()));
    }
}
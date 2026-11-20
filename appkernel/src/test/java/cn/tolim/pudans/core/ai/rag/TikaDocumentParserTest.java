package cn.tolim.pudans.core.ai.rag;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TikaDocumentParserTest {

    private final TikaDocumentParser parser = new TikaDocumentParser();

    @Test
    void supportsFormatsKnownToTika() {
        assertThat(parser.supports("page.html", "text/html")).isTrue();
        assertThat(parser.supports("page.html", null)).isTrue();
        assertThat(parser.supports(null, null)).isFalse();
    }

    @Test
    void extractsTextFromHtml() {
        var html = "<html><body><h1>标题</h1><p>正文内容</p></body></html>";

        var document = parser.parse(html.getBytes(UTF_8));

        assertThat(document.text()).contains("标题").contains("正文内容");
        assertThat(document.headings()).isEmpty();
        assertThat(document.pages()).isEmpty();
    }

    @Test
    void rejectsMissingContent() {
        assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
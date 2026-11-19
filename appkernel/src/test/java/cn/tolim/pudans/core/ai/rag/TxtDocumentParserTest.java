package cn.tolim.pudans.core.ai.rag;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TxtDocumentParserTest {

    private final TxtDocumentParser parser = new TxtDocumentParser();

    @Test
    void supportsTextMimeTypesAndExtensions() {
        assertThat(parser.supports("manual.TXT", null)).isTrue();
        assertThat(parser.supports("manual", "text/plain")).isTrue();
        assertThat(parser.supports("manual.md", "text/plain")).isFalse();
        assertThat(parser.supports("manual.pdf", "application/pdf")).isFalse();
    }

    @Test
    void decodesUtf8AndStripsTheBom() {
        var text = "你好世界".getBytes(UTF_8);
        var content = new byte[text.length + 3];
        content[0] = (byte) 0xEF;
        content[1] = (byte) 0xBB;
        content[2] = (byte) 0xBF;
        System.arraycopy(text, 0, content, 3, text.length);

        var document = parser.parse(content);

        assertThat(document.text()).isEqualTo("你好世界");
        assertThat(document.headings()).isEmpty();
        assertThat(document.pages()).isEmpty();
    }

    @Test
    void rejectsMissingContent() {
        assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }
}

package cn.tolim.pudans.core.ai.rag;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class WordDocumentParserTest {

    private final WordDocumentParser parser = new WordDocumentParser();

    @Test
    void supportsDocxMimeTypesAndExtensions() {
        assertThat(parser.supports("manual.DOCX", null)).isTrue();
        assertThat(parser.supports(
                        "manual",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .isTrue();
        assertThat(parser.supports("manual.doc", null)).isFalse();
        assertThat(parser.supports("manual.pdf", "application/pdf")).isFalse();
    }

    @Test
    void extractsHeadingsParagraphsAndTables() throws IOException {
        var content = docx();

        var document = parser.parse(content);

        assertThat(document.text()).contains("第一章").contains("正文内容");
        assertThat(document.headings()).singleElement().satisfies(heading -> {
            assertThat(heading.level()).isEqualTo(1);
            assertThat(heading.title()).isEqualTo("第一章");
            assertThat(heading.offset()).isZero();
        });
        assertThat(document.text()).contains("| 名称 | 数量 |").contains("| 甲 | 1 |");
        assertThat(document.pages()).isEmpty();
    }

    @Test
    void rejectsInvalidAndMissingContent() {
        assertThatThrownBy(() -> parser.parse("not a docx".getBytes(UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Word");
        assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] docx() throws IOException {
        try (var document = new XWPFDocument(); var output = new ByteArrayOutputStream()) {
            var heading = document.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("第一章");
            var paragraph = document.createParagraph();
            paragraph.createRun().setText("正文内容");
            var table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("名称");
            table.getRow(0).getCell(1).setText("数量");
            table.getRow(1).getCell(0).setText("甲");
            table.getRow(1).getCell(1).setText("1");
            document.write(output);
            return output.toByteArray();
        }
    }
}
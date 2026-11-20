package cn.tolim.pudans.core.ai.rag;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class PdfDocumentParserTest {

    private final PdfDocumentParser parser = new PdfDocumentParser();

    @Test
    void supportsPdfMimeTypesAndExtensions() {
        assertThat(parser.supports("manual.PDF", null)).isTrue();
        assertThat(parser.supports("manual", "application/pdf")).isTrue();
        assertThat(parser.supports("manual.docx", null)).isFalse();
        assertThat(parser.supports("manual.txt", "text/plain")).isFalse();
    }

    @Test
    void extractsTextAndPageOffsets() throws IOException {
        var content = pdf("First page", "Second page");

        var document = parser.parse(content);

        assertThat(document.text()).contains("First page").contains("Second page");
        assertThat(document.headings()).isEmpty();
        assertThat(document.pages()).extracting(ParsedDocument.Page::pageNo).containsExactly(1, 2);
        assertThat(document.pages().get(0).offset()).isZero();
        assertThat(document.pages().get(1).offset()).isPositive();
    }

    @Test
    void rejectsInvalidAndMissingContent() {
        assertThatThrownBy(() -> parser.parse("not a pdf".getBytes(UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PDF");
        assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] pdf(String... pageTexts) throws IOException {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            for (var pageText : pageTexts) {
                var page = new PDPage();
                document.addPage(page);
                try (var stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(72, 700);
                    stream.showText(pageText);
                    stream.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
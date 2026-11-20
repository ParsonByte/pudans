package cn.tolim.pudans.core.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageDocumentParserTest {

    private final ImageDocumentParser parser = new ImageDocumentParser();

    @Test
    void supportsImageMimeTypesAndExtensions() {
        assertThat(parser.supports("photo.PNG", null)).isTrue();
        assertThat(parser.supports("photo", "image/jpeg")).isTrue();
        assertThat(parser.supports("manual.pdf", "application/pdf")).isFalse();
        assertThat(parser.supports("manual.txt", "text/plain")).isFalse();
    }

    @Test
    void parsesImageContentWithoutTextWhenOcrIsUnavailable() throws IOException {
        var document = parser.parse(png());

        assertThat(document.text()).isEmpty();
        assertThat(document.headings()).isEmpty();
        assertThat(document.pages()).isEmpty();
    }

    @Test
    void rejectsMissingContent() {
        assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] png() throws IOException {
        var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        try (var output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }
}
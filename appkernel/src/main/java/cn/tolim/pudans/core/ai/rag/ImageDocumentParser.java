package cn.tolim.pudans.core.ai.rag;

import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(4)
public class ImageDocumentParser implements DocumentParser {

    private static final String MIME_PREFIX = "image/";

    private static final String[] EXTENSIONS = {".jpg", ".jpeg", ".png", ".gif", ".bmp", ".tif", ".tiff", ".webp"};

    @Override
    public boolean supports(String fileName, @Nullable String mimeType) {
        if (mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith(MIME_PREFIX)) {
            return true;
        }
        return DocumentParser.hasExtension(fileName, EXTENSIONS);
    }

    @Override
    public ParsedDocument parse(byte[] content) {
        return ParsedDocument.of(TikaTextExtractor.extract(content));
    }
}
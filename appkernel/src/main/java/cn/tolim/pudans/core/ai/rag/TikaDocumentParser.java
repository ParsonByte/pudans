package cn.tolim.pudans.core.ai.rag;

import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TikaDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String fileName, @Nullable String mimeType) {
        return fileName != null || mimeType != null;
    }

    @Override
    public ParsedDocument parse(byte[] content) {
        return ParsedDocument.of(TikaTextExtractor.extract(content));
    }
}
package cn.tolim.pudans.core.ai.rag;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class TxtDocumentParser implements DocumentParser {

    private static final String MIME_TYPE = "text/plain";

    @Override
    public boolean supports(String fileName, @Nullable String mimeType) {
        if (DocumentParser.hasExtension(fileName, ".md", ".markdown")) {
            return false;
        }
        return MIME_TYPE.equalsIgnoreCase(mimeType) || DocumentParser.hasExtension(fileName, ".txt");
    }

    @Override
    public ParsedDocument parse(byte[] content) {
        return ParsedDocument.of(DocumentParser.decodeUtf8(content));
    }
}

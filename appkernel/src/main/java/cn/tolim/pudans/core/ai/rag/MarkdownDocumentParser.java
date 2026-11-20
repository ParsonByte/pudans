package cn.tolim.pudans.core.ai.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class MarkdownDocumentParser implements DocumentParser {

    private static final Set<String> MIME_TYPES = Set.of("text/markdown", "text/x-markdown");

    private static final int MAX_HEADING_LEVEL = 6;

    private static final String FENCE = "```";

    private static final String TILDE_FENCE = "~~~";

    @Override
    public boolean supports(String fileName, @Nullable String mimeType) {
        var normalizedMimeType = mimeType == null ? null : mimeType.toLowerCase(Locale.ROOT);
        return (normalizedMimeType != null && MIME_TYPES.contains(normalizedMimeType))
                || DocumentParser.hasExtension(fileName, ".md", ".markdown");
    }

    @Override
    public ParsedDocument parse(byte[] content) {
        var text = DocumentParser.decodeUtf8(content);
        var headings = new ArrayList<ParsedDocument.Heading>();
        var inFence = false;
        var lineStart = 0;
        while (lineStart <= text.length()) {
            var lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = text.length();
            }
            var line = text.substring(lineStart, lineEnd);
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            var stripped = line.stripLeading();
            if (stripped.startsWith(FENCE) || stripped.startsWith(TILDE_FENCE)) {
                inFence = !inFence;
            } else if (!inFence) {
                var heading = heading(line, lineStart);
                if (heading != null) {
                    headings.add(heading);
                }
            }
            if (lineEnd >= text.length()) {
                break;
            }
            lineStart = lineEnd + 1;
        }
        return new ParsedDocument(text, headings, List.of());
    }

    private static ParsedDocument.Heading heading(String line, int offset) {
        var level = 0;
        while (level < line.length() && level < MAX_HEADING_LEVEL && line.charAt(level) == '#') {
            level++;
        }
        if (level == 0 || level >= line.length() || !Character.isWhitespace(line.charAt(level))) {
            return null;
        }
        var title = line.substring(level).strip();
        if (title.isEmpty()) {
            return null;
        }
        return new ParsedDocument.Heading(offset, level, title);
    }
}

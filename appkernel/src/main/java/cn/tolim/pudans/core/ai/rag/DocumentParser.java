package cn.tolim.pudans.core.ai.rag;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

public interface DocumentParser {

    boolean supports(String fileName, @Nullable String mimeType);

    ParsedDocument parse(byte[] content);

    static String decodeUtf8(byte[] content) {
        if (content == null) {
            throw new IllegalArgumentException("文档内容不能为空。");
        }
        var offset = hasUtf8Bom(content) ? 3 : 0;
        return new String(content, offset, content.length - offset, StandardCharsets.UTF_8);
    }

    static boolean hasExtension(String fileName, String... extensions) {
        if (fileName == null) {
            return false;
        }
        var lowerCase = fileName.toLowerCase(Locale.ROOT);
        return Arrays.stream(extensions).anyMatch(lowerCase::endsWith);
    }

    private static boolean hasUtf8Bom(byte[] content) {
        return content.length >= 3
                && (content[0] & 0xFF) == 0xEF
                && (content[1] & 0xFF) == 0xBB
                && (content[2] & 0xFF) == 0xBF;
    }
}

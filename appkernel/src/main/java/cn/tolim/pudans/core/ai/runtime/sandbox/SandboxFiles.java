package cn.tolim.pudans.core.ai.runtime.sandbox;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.jspecify.annotations.Nullable;

public final class SandboxFiles {

    public static final String UTF8 = "utf-8";

    public static final String BASE64 = "base64";

    private SandboxFiles() {}

    static SandboxFile fromBytes(String path, byte[] content) {
        var text = utf8Text(content);
        if (text != null) {
            return new SandboxFile(path, UTF8, text, content.length);
        }
        return new SandboxFile(path, BASE64, Base64.getEncoder().encodeToString(content), content.length);
    }

    public static byte[] decode(String encoding, String content) {
        if (content == null) {
            throw SandboxErrors.configInvalid("文件 content 不能为空。");
        }
        if (UTF8.equalsIgnoreCase(encoding) || encoding == null || encoding.isBlank()) {
            return content.getBytes(StandardCharsets.UTF_8);
        }
        if (BASE64.equalsIgnoreCase(encoding)) {
            try {
                return Base64.getDecoder().decode(content);
            } catch (IllegalArgumentException error) {
                throw SandboxErrors.configInvalid("文件 content 不是合法的 base64。");
            }
        }
        throw SandboxErrors.configInvalid("文件 encoding 只支持 utf-8 或 base64。");
    }

    static @Nullable String utf8Text(byte[] content) {
        if (content == null || content.length == 0) {
            return "";
        }
        try {
            var decoder = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            var text = decoder.decode(ByteBuffer.wrap(content)).toString();
            return text.indexOf('\0') >= 0 ? null : text;
        } catch (CharacterCodingException error) {
            return null;
        }
    }
}
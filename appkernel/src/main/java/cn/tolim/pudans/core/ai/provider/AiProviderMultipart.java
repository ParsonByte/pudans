package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.infra.exception.ApiException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

public final class AiProviderMultipart {

    private AiProviderMultipart() {}

    // 保留原始文件名和媒体类型，供应商才能按声明校验文件。
    public static HttpEntity<FileSystemResource> filePart(
            String filename, String contentType, Path content) {
        var resource = new FileSystemResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        var headers = new HttpHeaders();
        headers.setContentType(contentType(contentType));
        return new HttpEntity<>(resource, headers);
    }

    public static boolean empty(Path content) {
        if (content == null) {
            return true;
        }
        try {
            return !Files.isRegularFile(content) || Files.size(content) == 0;
        } catch (IOException error) {
            return true;
        }
    }

    public static String filename(String value) {
        var filename = StringUtils.hasText(value) ? value.trim() : "file";
        if (filename.getBytes(StandardCharsets.UTF_8).length > 512) {
            throw invalid("文件名不能超过 512 字节。");
        }
        return filename;
    }

    public static MediaType contentType(String value) {
        if (!StringUtils.hasText(value)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(value);
        } catch (RuntimeException error) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST, "ai-provider-request-invalid", message);
    }
}

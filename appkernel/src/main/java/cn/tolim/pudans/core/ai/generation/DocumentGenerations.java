package cn.tolim.pudans.core.ai.generation;

import cn.tolim.pudans.core.ai.prompt.DocumentPrompts;
import cn.tolim.pudans.core.storage.StorageTransfer;
import cn.tolim.pudans.core.storage.upload.StorageWriteRequest;
import cn.tolim.pudans.core.infra.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class DocumentGenerations {

    private static final int MAX_OUTPUT_TOKENS = 4096;

    private static final double TEMPERATURE = 0.3;

    private static final int TITLE_LIMIT = 60;

    private static final String MODULE = "ai-generation";

    private static final Pattern UNSAFE_FILE_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\s]+");

    private static final String CODE_INVALID = "ai-document-generation-invalid";

    private final TextGenerations textGenerations;

    private final StorageTransfer storageTransfer;

    public enum Format {
        MARKDOWN,
        HTML
    }

    public record Command(
            String userId,
            String title,
            String prompt,
            @Nullable Format format,
            @Nullable String routeGroup) {}

    public record Result(
            String requestId,
            String fileId,
            String name,
            String mimeType,
            long size,
            @Nullable String modelCode) {}

    public Mono<Result> generate(Command command) {
        if (!StringUtils.hasText(command.userId())) {
            return Mono.error(invalid("用户不能为空。"));
        }
        if (!StringUtils.hasText(command.prompt())) {
            return Mono.error(invalid("生成提示不能为空。"));
        }
        var format = command.format() == null ? Format.MARKDOWN : command.format();
        var title = StringUtils.hasText(command.title()) ? command.title() : "文档";
        var requestId = "doc-" + UUID.randomUUID();
        return textGenerations
                .generate(new GenerationRequest(
                        command.routeGroup(), systemPrompt(format), command.prompt(), MAX_OUTPUT_TOKENS, TEMPERATURE))
                .flatMap(result -> {
                    var content = normalize(result.content(), format);
                    var bytes = content.getBytes(StandardCharsets.UTF_8);
                    var name = fileName(title, format);
                    var mediaType = mediaType(format);
                    return storageTransfer
                            .upload(new StorageWriteRequest(
                                    null,
                                    command.userId(),
                                    MODULE,
                                    null,
                                    name,
                                    mediaType,
                                    bytes.length,
                                    Map.of("purpose", "document-generation", "format", format.name()),
                                    Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(bytes)),
                                    false))
                            .map(file -> new Result(
                                    requestId,
                                    file.getMetadata().getName(),
                                    name,
                                    mediaType,
                                    bytes.length,
                                    result.modelCode()));
                });
    }

    private static String systemPrompt(Format format) {
        return switch (format) {
            case MARKDOWN -> DocumentPrompts.MARKDOWN;
            case HTML -> DocumentPrompts.HTML;
        };
    }

    private static String mediaType(Format format) {
        return switch (format) {
            case MARKDOWN -> "text/markdown";
            case HTML -> "text/html";
        };
    }

    private static String fileName(String title, Format format) {
        var base = UNSAFE_FILE_CHARS.matcher(title.strip()).replaceAll("-");
        if (base.isBlank()) {
            base = "document";
        }
        if (base.length() > TITLE_LIMIT) {
            base = base.substring(0, TITLE_LIMIT);
        }
        var extension = format == Format.MARKDOWN ? ".md" : ".html";
        return base.toLowerCase().endsWith(extension) ? base : base + extension;
    }

    private static String normalize(String content, Format format) {
        var text = content == null ? "" : content.strip();
        if (!text.startsWith("```")) {
            return text;
        }
        var firstBreak = text.indexOf('\n');
        if (firstBreak < 0) {
            return text;
        }
        var closing = text.lastIndexOf("```");
        if (closing <= firstBreak) {
            return text;
        }
        return text.substring(firstBreak + 1, closing).strip();
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, CODE_INVALID, message);
    }
}
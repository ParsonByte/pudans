package cn.tolim.pudans.core.ai.library.skill;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;

import cn.tolim.pudans.contract.ai.AiSkill;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.provider.AiProviderHttpSupport;
import cn.tolim.pudans.core.ai.provider.AiWebClientConfiguration;
import cn.tolim.pudans.core.infra.exception.ApiException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class Skills {

    static final String CODE_INVALID = "ai-skill-invalid";

    static final String CODE_IMPORT_INVALID = "ai-skill-import-invalid";

    static final String CODE_IMPORT_TOO_LARGE = "ai-skill-import-too-large";

    static final String CODE_IMPORT_UNSUPPORTED = "ai-skill-import-unsupported";

    static final String CODE_IMPORT_FAILED = "ai-skill-import-failed";

    static final int MAX_CONTENT_BYTES = 1024 * 1024;

    static final Duration IMPORT_TIMEOUT = Duration.ofSeconds(15);

    private static final MediaType TEXT_MARKDOWN = MediaType.parseMediaType("text/markdown");

    private final ReactiveExtensionClient client;

    private final WebClient webClient;

    public Skills(
            ReactiveExtensionClient client,
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient) {
        this.client = client;
        this.webClient = Objects.requireNonNull(webClient, "AI WebClient 不能为空");
    }

    public record SkillSummary(
            @Nullable String name,
            @Nullable String displayName,
            @Nullable String description,
            @Nullable String content) {}

    record SkillDraft(@Nullable String name, @Nullable String description) {}

    public static Mono<Void> validate(AiSkill skill) {
        return Mono.fromRunnable(() -> {
            var spec = skill == null ? null : skill.getSpec();
            if (spec == null) {
                throw invalid("技能配置不能为空。");
            }
            if (!StringUtils.hasText(spec.getDisplayName())) {
                throw invalid("技能 displayName 不能为空。");
            }
            var source = spec.getSource();
            if (source == null) {
                throw invalid("技能 source 不能为空。");
            }
            switch (source) {
                case INLINE -> {
                    if (!StringUtils.hasText(spec.getContent())) {
                        throw invalid("INLINE 技能必须配置 content。");
                    }
                }
                case URL, GIT -> {
                    if (!StringUtils.hasText(spec.getUrl())) {
                        throw invalid(source + " 技能必须配置 url。");
                    }
                    requireHttpUrl(spec.getUrl());
                }
            }
            requireNoBlankEntries(spec.getAgents());
        });
    }

    public Flux<AiSkill> byAgent(String agentName) {
        return enabledSkills()
                .filter(skill -> skill.getSpec().getAgents() != null
                        && skill.getSpec().getAgents().contains(agentName));
    }

    public Mono<List<AiSkill>> byNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return Mono.just(List.of());
        }
        var wanted = Set.copyOf(names);
        return enabledSkills()
                .filter(skill -> wanted.contains(skill.getMetadata().getName()))
                .collectList();
    }

    private Flux<AiSkill> enabledSkills() {
        return client.listAll(AiSkill.class, ListOptions.builder().build(), defaultSort())
                .filter(skill -> skill.getSpec() != null
                        && skill.getSpec().isEnabled()
                        && skill.getSpec().getStatus() == AiSkill.Status.PUBLISHED);
    }

    public Flux<SkillSummary> skillsForAgent(String agentName) {
        return byAgent(agentName).map(skill -> new SkillSummary(
                skill.getMetadata() == null ? null : skill.getMetadata().getName(),
                skill.getSpec().getDisplayName(),
                skill.getSpec().getDescription(),
                skill.getSpec().getContent()));
    }

    public Mono<AiSkill> importFromUrl(String url, @Nullable String ref) {
        return Mono.defer(() -> {
                    URI uri;
                    try {
                        uri = URI.create(AiProviderHttpSupport.requireHttpUrl(url, "技能导入 url"));
                    } catch (IllegalArgumentException error) {
                        throw importInvalid(error.getMessage());
                    }
                    return webClient
                            .get()
                            .uri(uri)
                            .accept(TEXT_MARKDOWN, MediaType.TEXT_PLAIN)
                            .exchangeToMono(response -> {
                                if (response.statusCode().isError()) {
                                    return response.releaseBody()
                                            .then(Mono.error(importFailed("技能导入返回 HTTP "
                                                    + response.statusCode().value() + "。")));
                                }
                                var contentType = response.headers().contentType().orElse(null);
                                if (contentType != null && !"text".equalsIgnoreCase(contentType.getType())) {
                                    return response.releaseBody()
                                            .then(Mono.error(importUnsupported()));
                                }
                                return DataBufferUtils.join(
                                                response.bodyToFlux(DataBuffer.class), MAX_CONTENT_BYTES)
                                        .switchIfEmpty(Mono.error(importInvalid("技能导入内容为空。")))
                                        .map(Skills::readContent);
                            })
                            .timeout(IMPORT_TIMEOUT)
                            .map(content -> draft(uri, content, ref));
                })
                .onErrorMap(DataBufferLimitException.class, error -> importTooLarge())
                .onErrorMap(TimeoutException.class, error -> new ApiException(
                        HttpStatus.BAD_GATEWAY, CODE_IMPORT_FAILED, "技能导入超时。", error))
                .onErrorMap(error -> error instanceof ApiException
                        ? error
                        : new ApiException(
                                HttpStatus.BAD_GATEWAY, CODE_IMPORT_FAILED, "技能导入失败。", error));
    }

    static @Nullable SkillDraft parseFrontmatter(@Nullable String content) {
        if (content == null) {
            return null;
        }
        var lines = content.split("\n", -1);
        var start = 0;
        while (start < lines.length && lines[start].isBlank()) {
            start++;
        }
        if (start >= lines.length || !"---".equals(lines[start].strip())) {
            return null;
        }
        String name = null;
        String description = null;
        for (var index = start + 1; index < lines.length; index++) {
            var line = lines[index].strip();
            if ("---".equals(line)) {
                return new SkillDraft(name, description);
            }
            var colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            var key = line.substring(0, colon).strip();
            var value = unquote(line.substring(colon + 1).strip());
            if ("name".equals(key)) {
                name = value;
            } else if ("description".equals(key)) {
                description = value;
            }
        }
        return null;
    }

    static String fallbackName(URI uri) {
        var path = uri.getPath();
        var segment = path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
        var dot = segment.lastIndexOf('.');
        var name = dot > 0 ? segment.substring(0, dot) : segment;
        if (StringUtils.hasText(name)) {
            return name;
        }
        return StringUtils.hasText(uri.getHost()) ? uri.getHost() : "skill";
    }

    private static AiSkill draft(URI uri, String content, @Nullable String ref) {
        var parsed = parseFrontmatter(content);
        var spec = new AiSkill.Spec();
        spec.setDisplayName(parsed != null && StringUtils.hasText(parsed.name())
                ? parsed.name()
                : fallbackName(uri));
        spec.setDescription(parsed == null ? null : parsed.description());
        spec.setSource(AiSkill.Source.URL);
        spec.setUrl(uri.toString());
        spec.setRef(ref);
        spec.setContent(content);
        var skill = new AiSkill();
        skill.setSpec(spec);
        return skill;
    }

    private static String readContent(DataBuffer buffer) {
        try {
            var bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } finally {
            DataBufferUtils.release(buffer);
        }
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            var first = value.charAt(0);
            var last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static void requireHttpUrl(String url) {
        try {
            AiProviderHttpSupport.requireHttpUrl(url, "技能 url");
        } catch (IllegalArgumentException error) {
            throw invalid(error.getMessage());
        }
    }

    private static void requireNoBlankEntries(@Nullable List<String> values) {
        if (values != null && values.stream().anyMatch(value -> !StringUtils.hasText(value))) {
            throw invalid("agents 不能包含空值。");
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, CODE_INVALID, message);
    }

    private static ApiException importInvalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, CODE_IMPORT_INVALID, message);
    }

    private static ApiException importTooLarge() {
        return new ApiException(
                HttpStatus.PAYLOAD_TOO_LARGE, CODE_IMPORT_TOO_LARGE, "技能导入内容超过 1 MB 限制。");
    }

    private static ApiException importUnsupported() {
        return new ApiException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, CODE_IMPORT_UNSUPPORTED, "技能导入只支持文本内容。");
    }

    private static ApiException importFailed(String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY, CODE_IMPORT_FAILED, message);
    }
}
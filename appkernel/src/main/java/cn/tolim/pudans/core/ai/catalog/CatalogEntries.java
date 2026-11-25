package cn.tolim.pudans.core.ai.catalog;

import cn.tolim.pudans.contract.ai.CatalogEntry;
import cn.tolim.pudans.contract.ai.CatalogKind;
import cn.tolim.pudans.contract.ai.dto.CatalogEntryCreateRequest;
import cn.tolim.pudans.contract.ai.dto.CatalogEntryUpdateRequest;
import cn.tolim.pudans.core.ai.AiSupport;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.catalog.CatalogEntryRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class CatalogEntries {

    private static final Pattern KEY_PATTERN =
            Pattern.compile("^[a-z0-9][a-z0-9._-]{0,127}$");

    private final CatalogEntryRepository entries;

    private Clock clock = Clock.systemUTC();

    public Flux<CatalogEntry> list(String catalogKind) {
        return StringUtils.hasText(catalogKind)
                ? entries.findAllByKind(kind(catalogKind))
                : entries.findAll();
    }

    public Mono<CatalogEntry> get(UUID id) {
        return entries
                .findById(id)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND,
                        "catalog-entry-not-found",
                        "目录项不存在。")));
    }

    public Mono<CatalogEntry> create(CatalogEntryCreateRequest request) {
        if (request == null) {
            return Mono.error(bodyRequired());
        }
        return Mono.defer(() -> {
            var now = Instant.now(clock);
            var validFrom = request.validFrom();
            var validTo = request.validTo();
            validateWindow(validFrom, validTo);
            return entries.insert(new CatalogEntry(
                    UUID.randomUUID(),
                    kind(request.catalogKind()),
                    key(request.entryKey()),
                    AiSupport.requiredText(
                            request.label(),
                            "catalog-entry-label-required",
                            "目录项显示名称不能为空。"),
                    request.description(),
                    optionalKey(request.parentKey()),
                    request.sortOrder() == null
                            ? 100
                            : AiSupport.nonNegative(
                                    request.sortOrder(),
                                    "catalog-entry-sort-invalid",
                                    "目录项排序不能为负数。"),
                    request.enabled() == null || request.enabled(),
                    request.metadata() == null ? Map.of() : Map.copyOf(request.metadata()),
                    validFrom,
                    validTo,
                    now,
                    now));
        });
    }

    public Mono<CatalogEntry> update(UUID id, CatalogEntryUpdateRequest request) {
        if (request == null) {
            return Mono.error(bodyRequired());
        }
        return Mono.defer(() -> get(id).flatMap(existing -> {
            var validFrom = request.validFrom() == null
                    ? existing.validFrom()
                    : request.validFrom();
            var validTo = request.validTo() == null
                    ? existing.validTo()
                    : request.validTo();
            validateWindow(validFrom, validTo);
            return entries.update(new CatalogEntry(
                    existing.id(),
                    request.catalogKind() == null
                            ? existing.catalogKind()
                            : kind(request.catalogKind()),
                    request.entryKey() == null
                            ? existing.entryKey()
                            : key(request.entryKey()),
                    StringUtils.hasText(request.label())
                            ? request.label().trim()
                            : existing.label(),
                    request.description() == null
                            ? existing.description()
                            : request.description(),
                    request.parentKey() == null
                            ? existing.parentKey()
                            : optionalKey(request.parentKey()),
                    request.sortOrder() == null
                            ? existing.sortOrder()
                            : AiSupport.nonNegative(
                                    request.sortOrder(),
                                    "catalog-entry-sort-invalid",
                                    "目录项排序不能为负数。"),
                    request.enabled() == null ? existing.enabled() : request.enabled(),
                    request.metadata() == null
                            ? existing.metadata()
                            : Map.copyOf(request.metadata()),
                    validFrom,
                    validTo,
                    existing.createdAt(),
                    Instant.now(clock)));
        }));
    }

    public Mono<Void> delete(UUID id) {
        return get(id).flatMap(entry -> entries.delete(entry.id()));
    }

    private static String kind(String value) {
        var normalized = AiSupport.requiredText(
                        value,
                        "catalog-entry-kind-invalid",
                        "目录类型不能为空。")
                .toLowerCase(Locale.ROOT);
        if (CatalogKind.fromCode(normalized).isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "catalog-entry-kind-invalid",
                    "不支持的目录类型。");
        }
        return normalized;
    }

    private static String key(String value) {
        var normalized = AiSupport.requiredText(
                value, "catalog-entry-key-invalid", "目录键不能为空。");
        if (!KEY_PATTERN.matcher(normalized).matches()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "catalog-entry-key-invalid",
                    "目录键只能包含小写字母、数字、点、下划线和连字符。");
        }
        return normalized;
    }

    private static String optionalKey(String value) {
        return StringUtils.hasText(value) ? key(value) : null;
    }

    private static void validateWindow(Instant validFrom, Instant validTo) {
        if (validFrom != null && validTo != null && !validTo.isAfter(validFrom)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "catalog-entry-validity-invalid",
                    "目录项失效时间必须晚于生效时间。");
        }
    }

    private static ApiException bodyRequired() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "catalog-entry-body-required",
                "请求体不能为空。");
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }
}

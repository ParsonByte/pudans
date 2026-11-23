package cn.tolim.pudans.extension;

import static cn.tolim.pudans.extension.contract.ExtensionUtil.defaultSort;

import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.ListResult;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.Secret;
import cn.tolim.pudans.extension.contract.SecretCatalog;
import cn.tolim.pudans.extension.contract.SecretCatalogItem;
import cn.tolim.pudans.extension.contract.SecretCatalogSummary;
import cn.tolim.pudans.extension.contract.SecretTypeCount;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class SecretDirectory {

    public static final int MAX_PAGE_SIZE = 100;

    private static final String DEFAULT_TYPE = "Opaque";

    private static final Duration RECENT_WINDOW = Duration.ofDays(7);

    private static final Comparator<SecretTypeCount> TYPE_COUNT_ORDER =
            Comparator.comparingLong(SecretTypeCount::count)
                    .reversed()
                    .thenComparing(SecretTypeCount::type);

    private final ReactiveExtensionClient client;

    // 读取全部密钥并生成不含值的分页目录。
    public Mono<SecretCatalog> catalog(String keyword, String type, int page, int size) {
        return client.listAll(Secret.class, ListOptions.builder().build(), defaultSort())
                .collectList()
                .map(secrets -> buildCatalog(secrets, keyword, type, page, size));
    }

    // 先汇总全部密钥，再按条件过滤并截取目标页。
    private static SecretCatalog buildCatalog(
            List<Secret> secrets, String keyword, String type, int page, int size) {
        var generatedAt = Instant.now();
        var resolvedPage = Math.max(1, page);
        var resolvedSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        var items = secrets.stream().map(SecretDirectory::toCatalogItem).toList();
        var filtered = items.stream()
                .filter(item -> !StringUtils.hasText(type) || type.equals(item.type()))
                .filter(item -> matchesKeyword(item, keyword))
                .toList();
        var total = filtered.size();
        var totalPages = (total + (long) resolvedSize - 1) / resolvedSize;
        var pageItems = resolvedPage > totalPages
                ? List.<SecretCatalogItem>of()
                : List.copyOf(ListResult.subList(filtered, resolvedPage, resolvedSize));
        return new SecretCatalog(
                resolvedPage,
                resolvedSize,
                total,
                totalPages,
                generatedAt,
                summarize(items, generatedAt),
                pageItems);
    }

    // 汇总全部密钥的类型、键数量与近期创建数量。
    private static SecretCatalogSummary summarize(List<SecretCatalogItem> items, Instant generatedAt) {
        var cutoff = generatedAt.minus(RECENT_WINDOW);
        var typeCounts = new HashMap<String, SecretTypeCount>();
        long entries = 0;
        long empty = 0;
        long createdLastSevenDays = 0;
        for (var item : items) {
            var itemEntries = item.keys().size();
            entries += itemEntries;
            if (itemEntries == 0) {
                empty++;
            }
            if (item.creationTimestamp() != null && !item.creationTimestamp().isBefore(cutoff)) {
                createdLastSevenDays++;
            }
            typeCounts.merge(
                    item.type(),
                    new SecretTypeCount(item.type(), 1, itemEntries),
                    (left, right) -> new SecretTypeCount(
                            left.type(),
                            left.count() + right.count(),
                            left.entries() + right.entries()));
        }
        var types = typeCounts.values().stream().sorted(TYPE_COUNT_ORDER).toList();
        return new SecretCatalogSummary(items.size(), entries, empty, createdLastSevenDays, types);
    }

    // 把密钥转换为仅含元数据和键名的目录项。
    private static SecretCatalogItem toCatalogItem(Secret secret) {
        var metadata = secret.getMetadata();
        var dataKeys = sortedKeys(secret.getData());
        var stringDataKeys = sortedKeys(secret.getStringData());
        var keys = new TreeSet<>(dataKeys);
        keys.addAll(stringDataKeys);
        return new SecretCatalogItem(
                metadata == null ? null : metadata.getName(),
                StringUtils.hasText(secret.getType()) ? secret.getType() : DEFAULT_TYPE,
                List.copyOf(keys),
                stringDataKeys,
                dataKeys,
                !stringDataKeys.isEmpty(),
                !dataKeys.isEmpty(),
                metadata == null || metadata.getLabels() == null ? Map.of() : Map.copyOf(metadata.getLabels()),
                metadata == null || metadata.getAnnotations() == null
                        ? Map.of()
                        : Map.copyOf(metadata.getAnnotations()),
                metadata == null ? null : metadata.getCreationTimestamp(),
                metadata == null || metadata.getVersion() == null ? 0L : metadata.getVersion());
    }

    // 返回排序后的键名，不读取键值。
    private static List<String> sortedKeys(Map<String, ?> values) {
        return values == null || values.isEmpty()
                ? List.of()
                : values.keySet().stream().sorted().toList();
    }

    // 关键字不区分大小写匹配名称或任一键名。
    private static boolean matchesKeyword(SecretCatalogItem item, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        var needle = keyword.toLowerCase(Locale.ROOT);
        return containsIgnoreCase(item.name(), needle)
                || item.keys().stream().anyMatch(key -> containsIgnoreCase(key, needle));
    }

    // 比较小写文本，避免依赖默认区域设置。
    private static boolean containsIgnoreCase(String value, String lowercaseNeedle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowercaseNeedle);
    }
}

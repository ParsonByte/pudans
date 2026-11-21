package cn.tolim.pudans.core.storage;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.core.infra.utils.PudansTime;

@Component
public class StoragePolicyPathGenerator {

    private static final Pattern VARIABLE = Pattern.compile("\\{[^{}]+}");

    private static final String ALPHANUM = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Set<String> ALLOWED_VARIABLES = Set.of(
            "{randomkey16}",
            "{randomkey8}",
            "{randomnum2}",
            "{randomnum3}",
            "{randomnum4}",
            "{randomnum8}",
            "{uuid}",
            "{uid}",
            "{timestamp}",
            "{timestamp_nano}",
            "{datetime}",
            "{date}",
            "{year}",
            "{month}",
            "{day}",
            "{hour}",
            "{minute}",
            "{second}",
            "{originname}",
            "{originname_without_ext}",
            "{ext}",
            "{path}");

    private static final Set<String> UNIQUE_VARIABLES =
            Set.of("{randomkey16}", "{randomkey8}", "{uuid}", "{timestamp}", "{timestamp_nano}");

    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM");

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd");

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH");

    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("mm");

    private static final DateTimeFormatter SECOND = DateTimeFormatter.ofPattern("ss");

    private static final int MAX_SEGMENT_LENGTH = 200;

    public String generate(StoragePolicy policy, StoragePathContext context) {
        var spec = policy.getSpec();
        var directory = render(spec.getDirNameRule(), context, true);
        var fileName = render(spec.getFileNameRule(), context, false);
        return normalizeKey(directory + "/" + fileName);
    }

    public List<String> validateRules(String dirNameRule, String fileNameRule) {
        var errors = new ArrayList<String>();
        validateRule("目录规则", dirNameRule, true, errors);
        validateRule("文件规则", fileNameRule, false, errors);
        if (!containsUniqueVariable(dirNameRule) && !containsUniqueVariable(fileNameRule)) {
            errors.add("目录规则和文件规则至少需要一个随机变量，避免文件互相覆盖。");
        }
        return errors;
    }

    private void validateRule(String label, String rule, boolean directoryRule, List<String> errors) {
        if (!StringUtils.hasText(rule)) {
            errors.add(label + "不能为空。");
            return;
        }
        if (rule.startsWith("/")) {
            errors.add(label + "不能以斜杠开头。");
        }
        if (rule.contains("\\")) {
            errors.add(label + "不能包含反斜杠。");
        }
        if (Arrays.asList(rule.split("/")).contains("..")) {
            errors.add(label + "不能包含上级目录。");
        }
        if (rule.length() > 512) {
            errors.add(label + "长度不能超过 512 个字符。");
        }
        var matcher = VARIABLE.matcher(rule);
        while (matcher.find()) {
            var variable = matcher.group();
            if (!ALLOWED_VARIABLES.contains(variable)) {
                errors.add(label + "包含不支持的变量 " + variable + "。");
            }
            if (!directoryRule && "{path}".equals(variable)) {
                errors.add("文件规则不能包含 {path}。");
            }
        }
    }

    private static boolean containsUniqueVariable(String rule) {
        return rule != null && UNIQUE_VARIABLES.stream().anyMatch(rule::contains);
    }

    private String render(String rule, StoragePathContext context, boolean pathAvailable) {
        return VARIABLE.matcher(rule).replaceAll(match -> Matcher.quoteReplacement(
                value(match.group(), context, pathAvailable)));
    }

    private String value(String variable, StoragePathContext context, boolean pathAvailable) {
        var time = LocalDateTime.ofInstant(context.now(), PudansTime.BEIJING);
        return switch (variable) {
            case "{randomkey16}" -> randomString(16);
            case "{randomkey8}" -> randomString(8);
            case "{randomnum2}" -> String.valueOf(RANDOM.nextInt(2));
            case "{randomnum3}" -> String.valueOf(RANDOM.nextInt(3));
            case "{randomnum4}" -> String.valueOf(RANDOM.nextInt(4));
            case "{randomnum8}" -> String.valueOf(RANDOM.nextInt(8));
            case "{uuid}" -> UUID.randomUUID().toString();
            case "{uid}" -> StringUtils.hasText(context.owner()) ? sanitizeName(context.owner()) : "";
            // {timestamp} 与 {timestamp_nano} 同为纳秒时间戳，避免秒级重名覆盖
            case "{timestamp}", "{timestamp_nano}" -> String.valueOf(epochNanos(context));
            case "{datetime}" -> DATETIME.format(time);
            case "{date}" -> DATE.format(time);
            case "{year}" -> YEAR.format(time);
            case "{month}" -> MONTH.format(time);
            case "{day}" -> DAY.format(time);
            case "{hour}" -> HOUR.format(time);
            case "{minute}" -> MINUTE.format(time);
            case "{second}" -> SECOND.format(time);
            case "{originname}" -> sanitizeName(context.originName());
            case "{originname_without_ext}" -> withoutExtension(sanitizeName(context.originName()));
            case "{ext}" -> extensionOf(context.originName());
            case "{path}" -> pathAvailable ? sanitizePath(context.originPath()) : "";
            default -> variable;
        };
    }

    private static long epochNanos(StoragePathContext context) {
        var now = context.now();
        return now.getEpochSecond() * 1_000_000_000L + now.getNano();
    }

    private static String randomString(int length) {
        var builder = new StringBuilder(length);
        for (var index = 0; index < length; index++) {
            builder.append(ALPHANUM.charAt(RANDOM.nextInt(ALPHANUM.length())));
        }
        return builder.toString();
    }

    private static String sanitizeName(String value) {
        if (!StringUtils.hasText(value)) {
            return "file";
        }
        var sanitized = value.replaceAll("[\\\\/\\p{Cntrl}]", "_").trim();
        if (!StringUtils.hasText(sanitized) || ".".equals(sanitized) || "..".equals(sanitized)) {
            return "file";
        }
        return sanitized.length() > MAX_SEGMENT_LENGTH ? sanitized.substring(0, MAX_SEGMENT_LENGTH) : sanitized;
    }

    private static String sanitizePath(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return Arrays.stream(value.replace('\\', '/').split("/"))
                .map(String::trim)
                .filter(part -> !part.isEmpty() && !".".equals(part) && !"..".equals(part))
                .collect(Collectors.joining("/"));
    }

    private static String withoutExtension(String value) {
        var dot = value.lastIndexOf('.');
        return dot <= 0 ? value : value.substring(0, dot);
    }

    private static String extensionOf(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        var dot = value.lastIndexOf('.');
        return dot < 0 ? "" : value.substring(dot);
    }

    private static String normalizeKey(String value) {
        var normalized = Arrays.stream(value.replace('\\', '/').split("/"))
                .map(String::trim)
                .filter(part -> !part.isEmpty() && !".".equals(part) && !"..".equals(part))
                .collect(Collectors.joining("/"));
        return normalized;
    }
}

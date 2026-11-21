package cn.tolim.pudans.core.storage;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.storage.StoragePolicyType;
import cn.tolim.pudans.core.storage.driver.StorageDriverFactoryRegistry;
import cn.tolim.pudans.core.webhook.OutboundRequestValidator;
import cn.tolim.pudans.infra.exception.ApiException;

@Component
public class StoragePolicyValidator {

    private static final long MEBIBYTE = 1024L * 1024L;

    private static final long S3_MIN_CHUNK = 5 * MEBIBYTE;

    private static final long MAX_CHUNK = 5L * 1024L * MEBIBYTE;

    private static final int MAX_PART_COUNT = 10_000;

    private static final int MAX_FILE_TYPES = 200;

    private static final int MAX_EXTENSION_LENGTH = 32;

    private static final int MAX_REGEXP_LENGTH = 512;

    private static final int MAX_FILE_NAME_LENGTH = 512;

    private final StorageDriverFactoryRegistry registry;

    private final StoragePolicyPathGenerator pathGenerator;

    // 注入驱动注册表与路径规则校验器。
    public StoragePolicyValidator(StorageDriverFactoryRegistry registry, StoragePolicyPathGenerator pathGenerator) {
        this.registry = registry;
        this.pathGenerator = pathGenerator;
    }

    // 汇总策略结构与驱动类型的全部校验错误。
    public ValidationResult validatePolicy(StoragePolicy policy) {
        var errors = new ArrayList<String>();
        if (policy == null || policy.getSpec() == null) {
            return new ValidationResult(false, List.of("存储策略不能为空。"));
        }
        var spec = policy.getSpec();
        if (!StringUtils.hasText(spec.getDisplayName())) {
            errors.add("显示名称不能为空。");
        }
        if (spec.getType() == null) {
            errors.add("存储类型不能为空。");
        } else if (registry.factory(spec.getType()).isEmpty()) {
            errors.add("不支持的存储类型：" + spec.getType().getCode() + "。");
        }
        if (spec.getMaxSize() < 0) {
            errors.add("单文件大小上限不能为负数。");
        }
        var settings = spec.getSettings();
        if (settings != null && (settings.getDeleteBatchSize() < 1 || settings.getDeleteBatchSize() > 1000)) {
            errors.add("批量删除数量必须在 1 到 1000 之间。");
        }
        if (spec.getChunkSize() < 0) {
            errors.add("分片大小不能为负数。");
        } else if (spec.getChunkSize() > 0) {
            validateChunkSize(spec, errors);
        }
        validateFileTypes(spec, errors);
        validateNameRegexp(spec, errors);
        validateEndpoint(spec, errors);
        validateCredentials(spec, errors);
        errors.addAll(pathGenerator.validateRules(spec.getDirNameRule(), spec.getFileNameRule()));
        return new ValidationResult(errors.isEmpty(), List.copyOf(errors));
    }

    // 校验上传大小、类型与文件名是否符合策略。
    public void validateUpload(StoragePolicy policy, String originalName, long size) {
        var spec = policy.getSpec();
        if (size < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "storage-file-size-invalid", "文件大小无效。");
        }
        if (spec.getMaxSize() > 0 && size > spec.getMaxSize()) {
            throw new ApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "storage-file-too-large",
                    "文件大小超过策略上限 " + spec.getMaxSize() + " 字节。");
        }
        if (originalName != null && originalName.length() > MAX_FILE_NAME_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "storage-file-name-too-long", "文件名过长。");
        }
        var extension = normalizeExtension(originalName);
        var fileTypes = normalizedFileTypes(spec);
        if (!CollectionUtils.isEmpty(fileTypes)) {
            var listed = fileTypes.contains(extension);
            if (spec.isFileTypeDenyList() && listed) {
                throw new ApiException(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE, "storage-file-type-not-allowed", "不允许上传该文件类型。");
            }
            if (!spec.isFileTypeDenyList() && !listed) {
                throw new ApiException(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE, "storage-file-type-not-allowed", "不允许上传该文件类型。");
            }
        }
        if (StringUtils.hasText(spec.getNameRegexp())) {
            var matched = Pattern.compile(spec.getNameRegexp()).matcher(originalName == null ? "" : originalName).matches();
            if (spec.isNameRegexpDenyList() && matched) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, "storage-file-name-not-allowed", "文件名不符合策略限制。");
            }
            if (!spec.isNameRegexpDenyList() && !matched) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, "storage-file-name-not-allowed", "文件名不符合策略限制。");
            }
        }
    }

    // 返回配置的分片大小，未配置时按类型取默认。
    public long chunkSize(StoragePolicy policy) {
        var spec = policy.getSpec();
        if (spec.getChunkSize() > 0) {
            return spec.getChunkSize();
        }
        return switch (spec.getType()) {
            case LOCAL, UPYUN -> 8 * MEBIBYTE;
            case ONEDRIVE -> 10 * MEBIBYTE;
            default -> 25 * MEBIBYTE;
        };
    }

    // 计算分片数量并限制在服务端上限内。
    public int partCount(long size, long chunkSize) {
        if (size <= 0) {
            return 1;
        }
        var count = (size + chunkSize - 1) / chunkSize;
        if (count > MAX_PART_COUNT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "storage-too-many-parts", "分片数量超过上限。");
        }
        return (int) count;
    }

    // 提取小写扩展名，无扩展名时返回空串。
    public static String normalizeExtension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        var dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    // 按存储类型校验分片大小的上下限。
    private void validateChunkSize(StoragePolicy.Spec spec, List<String> errors) {
        var minimum = spec.getType() == StoragePolicyType.S3 || spec.getType() == StoragePolicyType.KS3
                ? S3_MIN_CHUNK
                : MEBIBYTE;
        if (spec.getChunkSize() < minimum) {
            errors.add("分片大小不能小于 " + minimum + " 字节。");
        }
        if (spec.getChunkSize() > MAX_CHUNK) {
            errors.add("分片大小不能超过 " + MAX_CHUNK + " 字节。");
        }
    }

    // 校验文件类型列表的数量、格式与长度。
    private void validateFileTypes(StoragePolicy.Spec spec, List<String> errors) {
        var fileTypes = spec.getFileTypes();
        if (fileTypes == null) {
            return;
        }
        if (fileTypes.size() > MAX_FILE_TYPES) {
            errors.add("文件类型数量不能超过 " + MAX_FILE_TYPES + " 个。");
        }
        for (var fileType : fileTypes) {
            if (!StringUtils.hasText(fileType)) {
                errors.add("文件类型不能包含空值。");
                continue;
            }
            var normalized = normalizeExtension("." + fileType);
            if (!normalized.equals(fileType.toLowerCase(Locale.ROOT))) {
                errors.add("文件类型必须是不带点的扩展名：" + fileType + "。");
            }
            if (normalized.length() > MAX_EXTENSION_LENGTH) {
                errors.add("文件类型过长：" + fileType + "。");
            }
        }
    }

    // 校验文件名正则的长度与语法。
    private void validateNameRegexp(StoragePolicy.Spec spec, List<String> errors) {
        if (!StringUtils.hasText(spec.getNameRegexp())) {
            return;
        }
        if (spec.getNameRegexp().length() > MAX_REGEXP_LENGTH) {
            errors.add("文件名正则长度不能超过 " + MAX_REGEXP_LENGTH + " 个字符。");
            return;
        }
        try {
            Pattern.compile(spec.getNameRegexp());
        } catch (PatternSyntaxException e) {
            errors.add("文件名正则无效：" + e.getDescription() + "。");
        }
    }

    // 校验服务地址格式并拒绝本机或内网地址。
    private void validateEndpoint(StoragePolicy.Spec spec, List<String> errors) {
        if (spec.getType() == null) {
            return;
        }
        if (spec.getType() != StoragePolicyType.LOCAL && !StringUtils.hasText(spec.getBucketName())) {
            errors.add("存储桶名称不能为空。");
        }
        var requiresEndpoint = switch (spec.getType()) {
            case OSS, COS, OBS, QINIU, UPYUN -> true;
            default -> false;
        };
        if (requiresEndpoint && !StringUtils.hasText(spec.getServer())) {
            errors.add("服务地址不能为空。");
        }
        if (!StringUtils.hasText(spec.getServer())) {
            return;
        }
        try {
            var uri = new URI(spec.getServer());
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                errors.add("服务地址必须是 http 或 https URL。");
                return;
            }
            var host = uri.getHost();
            if (!StringUtils.hasText(host)) {
                errors.add("服务地址必须包含主机名。");
                return;
            }
            if (uri.getUserInfo() != null) {
                errors.add("服务地址不能包含用户信息。");
            }
            if (OutboundRequestValidator.isForbiddenHost(host)) {
                errors.add("不允许使用服务地址 [" + host + "]。");
                return;
            }
            var literal = OutboundRequestValidator.stripBrackets(host);
            if (OutboundRequestValidator.isIpLiteral(literal)
                    && OutboundRequestValidator.isBlockedLiteral(literal)) {
                errors.add("服务地址不能指向本机或内网地址。");
            }
        } catch (URISyntaxException e) {
            errors.add("服务地址不是合法的 URL。");
        }
    }

    // 保存期只校验结构，解析结果留到建连前，避免启动时依赖外部 DNS。
    public void verifyEndpointReachable(StoragePolicy.Spec spec) {
        if (spec == null || !StringUtils.hasText(spec.getServer())) {
            return;
        }
        var host = endpointHost(spec.getServer());
        if (!StringUtils.hasText(host)) {
            return;
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "storage-endpoint-unresolvable",
                    "无法解析存储服务地址 [" + host + "]。");
        }
        for (var address : addresses) {
            if (OutboundRequestValidator.isBlocked(address)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "storage-endpoint-forbidden",
                        "存储服务地址 [" + host + "] 指向本机或内网地址。");
            }
        }
    }

    // 从服务地址提取主机名，格式错误返回空。
    private static String endpointHost(String server) {
        try {
            return OutboundRequestValidator.stripBrackets(new URI(server).getHost());
        } catch (URISyntaxException e) {
            return null;
        }
    }

    // 按存储类型校验必填凭据是否配置。
    private void validateCredentials(StoragePolicy.Spec spec, List<String> errors) {
        if (spec.getType() == null || spec.getType() == StoragePolicyType.LOCAL) {
            return;
        }
        var credentials = spec.getCredentials();
        if (credentials == null) {
            errors.add("存储凭据不能为空。");
            return;
        }
        switch (spec.getType()) {
            case UPYUN -> {
                require(credentials.getAccessKey(), "又拍云操作员", errors);
                require(credentials.getSecretKey(), "又拍云密码", errors);
            }
            case ONEDRIVE -> {
                require(credentials.getClientSecret(), "OneDrive 客户端密钥", errors);
                require(credentials.getToken(), "OneDrive 刷新令牌", errors);
            }
            default -> {
                require(credentials.getAccessKey(), "Access Key", errors);
                require(credentials.getSecretKey(), "Secret Key", errors);
            }
        }
    }

    // 记录缺失的必填配置项。
    private static void require(String value, String label, List<String> errors) {
        if (!StringUtils.hasText(value)) {
            errors.add(label + "不能为空。");
        }
    }

    // 归一化文件类型列表，去重并忽略空值。
    private static List<String> normalizedFileTypes(StoragePolicy.Spec spec) {
        if (CollectionUtils.isEmpty(spec.getFileTypes())) {
            return List.of();
        }
        return spec.getFileTypes().stream()
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    public record ValidationResult(boolean valid, List<String> errors) {}
}

package cn.tolim.pudans.setting;

import cn.tolim.pudans.extension.contract.ApiGroups;
import cn.tolim.pudans.infra.contract.ExternalUrlSupplier;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.PudansProperties;
import cn.tolim.pudans.infra.utils.Digests;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
public class SiteAssets {

    public static final String DEFAULT_SITE_ASSET_PATH = "site-assets";

    public static final String SITE_ASSET_URL_PREFIX = ApiGroups.API_PREFIX + "/site-assets/";

    private static final long MAX_SIZE = 2L * 1024 * 1024;

    private static final Set<String> FIELDS = Set.of("logoDark", "logoLight", "favicon", "iconMedium", "iconLarge");

    private static final Set<String> EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp", "svg", "ico");

    private static final MediaType WEBP = MediaType.parseMediaType("image/webp");

    private static final MediaType SVG = MediaType.parseMediaType("image/svg+xml");

    private static final MediaType ICO = MediaType.parseMediaType("image/x-icon");

    private final PudansProperties pudansProperties;

    private final ExternalUrlSupplier externalUrlSupplier;

    // 校验站点图片字段与格式后保存并返回绝对地址
    public Mono<String> store(String field, FilePart file, HttpRequest request) {
        try {
            if (!FIELDS.contains(field)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "site-asset-invalid-field", "不支持的站点图片字段。");
            }
            var format = format(file.headers().getContentType());
            return readBytes(file).flatMap(bytes -> {
                validateImage(format, bytes);
                return write(field, format, bytes, request);
            });
        } catch (ApiException error) {
            return Mono.error(error);
        }
    }

    // 校验文件名后读取站点图片，缺失或非法时按不存在处理
    public Mono<SiteAsset> load(String filename) {
        if (!isSafeFileName(filename)) {
            return Mono.error(notFound());
        }
        return Mono.fromCallable(() -> {
                    var directory = directory();
                    var path = directory.resolve(filename).normalize();
                    if (!path.startsWith(directory) || Files.notExists(path) || !Files.isRegularFile(path)) {
                        throw notFound();
                    }
                    return new SiteAsset(new FileSystemResource(path), mediaTypeFor(filename));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    // 将字段与内容类型映射为受支持的图片格式
    private static Format format(MediaType contentType) {
        if (contentType == null || !"image".equalsIgnoreCase(contentType.getType())) {
            throw invalidType();
        }
        var subtype = contentType.getSubtype().toLowerCase(Locale.ROOT);
        return switch (subtype) {
            case "png" -> Format.PNG;
            case "jpeg", "jpg" -> Format.JPEG;
            case "webp" -> Format.WEBP;
            case "svg+xml", "svg" -> Format.SVG;
            case "x-icon", "vnd.microsoft.icon", "ico" -> Format.ICO;
            default -> throw invalidType();
        };
    }

    // 读取文件内容，超出大小限制时拒绝
    private static Mono<byte[]> readBytes(FilePart file) {
        var declaredLength = file.headers().getContentLength();
        if (declaredLength > MAX_SIZE) {
            return Mono.error(tooLarge());
        }
        return DataBufferUtils.join(file.content(), (int) MAX_SIZE)
                .map(SiteAssets::toBytes)
                .onErrorMap(
                        org.springframework.core.io.buffer.DataBufferLimitException.class,
                        error -> tooLarge())
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.BAD_REQUEST, "site-asset-empty", "站点图片文件为空。")));
    }

    // 复制缓冲区字节并确保释放资源
    private static byte[] toBytes(DataBuffer buffer) {
        try {
            var bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            return bytes;
        } finally {
            DataBufferUtils.release(buffer);
        }
    }

    // 按声明格式校验文件头，避免伪装成图片的内容被公开访问
    private static void validateImage(Format format, byte[] bytes) {
        if (bytes.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "site-asset-empty", "站点图片文件为空。");
        }
        if (format == Format.SVG) {
            validateSvg(bytes);
            return;
        }
        var valid = switch (format) {
            case PNG -> matches(bytes, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
            case JPEG -> matches(bytes, 0, 0xFF, 0xD8, 0xFF);
            case WEBP -> matches(bytes, 0, 'R', 'I', 'F', 'F') && matches(bytes, 8, 'W', 'E', 'B', 'P');
            case ICO -> matches(bytes, 0, 0x00, 0x00, 0x01, 0x00);
            case SVG -> true;
        };
        if (!valid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "site-asset-invalid-image", "站点图片内容与格式不符。");
        }
    }

    // 比较指定偏移处的字节序列
    private static boolean matches(byte[] bytes, int offset, int... values) {
        if (bytes.length < offset + values.length) {
            return false;
        }
        for (var index = 0; index < values.length; index++) {
            if ((bytes[offset + index] & 0xFF) != values[index]) {
                return false;
            }
        }
        return true;
    }

    // 仅接受不含脚本和实体声明的 SVG 文本
    private static void validateSvg(byte[] bytes) {
        var text = new String(bytes, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        if (!text.contains("<svg")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "site-asset-invalid-image", "站点图片内容与格式不符。");
        }
        if (text.contains("<script") || text.contains("<!entity")) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "site-asset-svg-unsafe", "SVG 不能包含脚本或外部实体声明。");
        }
    }

    // 按内容摘要写入站点图片目录，返回可公开访问的绝对地址
    private Mono<String> write(String field, Format format, byte[] bytes, HttpRequest request) {
        return Mono.fromCallable(() -> {
                    var directory = directory();
                    Files.createDirectories(directory);
                    var filename = field + "-" + Digests.sha256Hex(bytes).substring(0, 16) + "." + format.extension;
                    var target = directory.resolve(filename).normalize();
                    if (!target.startsWith(directory)) {
                        throw new ApiException(
                                HttpStatus.BAD_REQUEST, "site-asset-invalid-path", "不允许使用该站点图片路径。");
                    }
                    Files.write(target, bytes);
                    var baseUrl = externalUrlSupplier.getURL(request).toString();
                    if (baseUrl.endsWith("/")) {
                        baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
                    }
                    return baseUrl + SITE_ASSET_URL_PREFIX + filename;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(IOException.class, error -> new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "site-asset-storage-failed", "无法存储站点图片。", error));
    }

    // 解析站点图片目录并限制在工作目录内
    private Path directory() {
        return pudansProperties
                .getWorkDir()
                .toAbsolutePath()
                .normalize()
                .resolve(DEFAULT_SITE_ASSET_PATH)
                .normalize();
    }

    // 按扩展名返回响应媒体类型
    private static MediaType mediaTypeFor(String filename) {
        return switch (extension(filename)) {
            case "png" -> MediaType.IMAGE_PNG;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "webp" -> WEBP;
            case "svg" -> SVG;
            case "ico" -> ICO;
            default -> throw notFound();
        };
    }

    // 只接受白名单扩展名和简单文件名
    private static boolean isSafeFileName(String filename) {
        return filename != null
                && filename.matches("[A-Za-z0-9._-]+")
                && EXTENSIONS.contains(extension(filename));
    }

    // 提取小写扩展名，无扩展名时为空
    private static String extension(String filename) {
        var dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static ApiException invalidType() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "site-asset-invalid-type",
                "仅允许使用 png、jpg、jpeg、webp、svg 和 ico 格式的站点图片。");
    }

    private static ApiException tooLarge() {
        return new ApiException(
                HttpStatus.PAYLOAD_TOO_LARGE, "site-asset-too-large", "站点图片大小不能超过 " + MAX_SIZE + " 字节。");
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "site-asset-not-found", "站点图片不存在。");
    }

    public record SiteAsset(Resource resource, MediaType mediaType) {}

    private enum Format {
        PNG("png"),
        JPEG("jpg"),
        WEBP("webp"),
        SVG("svg"),
        ICO("ico");

        private final String extension;

        Format(String extension) {
            this.extension = extension;
        }
    }
}

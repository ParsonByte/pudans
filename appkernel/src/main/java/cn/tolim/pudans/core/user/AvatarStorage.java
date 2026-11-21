package cn.tolim.pudans.core.user;

import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.extension.api.ApiGroups;
import cn.tolim.pudans.infra.api.ParameterSetting;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.PudansProperties;
import cn.tolim.pudans.infra.setting.ParameterGroups;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarStorage {

    public static final String DEFAULT_AVATAR_PATH = "avatars";

    public static final String AVATAR_URL_PREFIX = ApiGroups.API_PREFIX + "/avatars/";

    private static final int MIN_AVATAR_DIMENSION = 16;

    private static final int MAX_AVATAR_DIMENSION = 2048;

    private static final int DEFAULT_AVATAR_DIMENSION = 256;

    private static final long DEFAULT_AVATAR_MAX_SIZE = 2L * 1024 * 1024;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp");

    private final ParameterGroups parameterGroups;

    private final PudansProperties pudansProperties;

    private final WebpSupport webpSupport;

    public Mono<String> store(User user, FilePart file) {
        return parameterGroups.getSession().flatMap(session -> {
            validateContentType(file);
            var maxSize = maxSize(session);
            var dimension = dimension(session);
            return readBytes(file, maxSize)
                    .map(bytes -> resize(bytes, dimension))
                    .flatMap(bytes -> writeAvatar(user, session, bytes));
        });
    }

    public Mono<Void> deleteForUser(User user) {
        return parameterGroups
                .getSession()
                .flatMap(session -> Mono.fromRunnable(
                                () -> deleteAvatar(user.getSpec().getAvatar(), directory(session)))
                        .subscribeOn(Schedulers.boundedElastic()))
                .then();
    }

    public Mono<Resource> load(String filename) {
        if (!isSafeFileName(filename)) {
            return Mono.empty();
        }
        return parameterGroups.getSession().flatMap(session -> Mono.fromCallable(() -> {
            var path = directory(session).resolve(filename).normalize();
            if (!path.startsWith(directory(session)) || Files.notExists(path) || !Files.isRegularFile(path)) {
                throw new ApiException(HttpStatus.NOT_FOUND, "avatar-not-found", "头像不存在。");
            }
            return (Resource) new FileSystemResource(path);
        }).subscribeOn(Schedulers.boundedElastic()));
    }

    private void validateContentType(FilePart file) {
        var contentType = file.headers().getContentType();
        if (contentType == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "avatar-invalid-type", "头像内容类型为必填项。");
        }
        var subtype = contentType.getSubtype().toLowerCase(Locale.ROOT);
        if ("webp".equals(subtype) && !webpSupport.isAvailable()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "avatar-webp-unavailable",
                    "此服务器未安装 WebP 解码器。");
        }
        if (!"png".equals(subtype) && !"jpeg".equals(subtype) && !"jpg".equals(subtype) && !"webp".equals(subtype)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "avatar-invalid-type", "仅允许使用 png、jpg、jpeg 和 webp 格式的头像。");
        }
    }

    private static long maxSize(ParameterSetting.Session session) {
        var configured = session.getAvatarMaxSize();
        return configured == null || configured <= 0 ? DEFAULT_AVATAR_MAX_SIZE : configured;
    }

    private static int dimension(ParameterSetting.Session session) {
        var configured = session.getAvatarDimension();
        if (configured == null) {
            return DEFAULT_AVATAR_DIMENSION;
        }
        return Math.max(MIN_AVATAR_DIMENSION, Math.min(MAX_AVATAR_DIMENSION, configured));
    }

    private Mono<byte[]> readBytes(FilePart file, long maxSize) {
        var declaredLength = file.headers().getContentLength();
        if (declaredLength > maxSize) {
            return Mono.error(tooLarge(maxSize));
        }
        return DataBufferUtils.join(
                        file.content(), (int) Math.min(maxSize, Integer.MAX_VALUE))
                .map(buffer -> toBytes(buffer, maxSize))
                .onErrorMap(
                        org.springframework.core.io.buffer.DataBufferLimitException.class,
                        error -> tooLarge(maxSize))
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.BAD_REQUEST, "avatar-empty", "头像文件为空。")));
    }

    private static byte[] toBytes(DataBuffer buffer, long maxSize) {
        try {
            if (buffer.readableByteCount() > maxSize) {
                throw tooLarge(maxSize);
            }
            var bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            return bytes;
        } finally {
            DataBufferUtils.release(buffer);
        }
    }

    private static ApiException tooLarge(long maxSize) {
        return new ApiException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "avatar-too-large",
                "头像大小不能超过 " + maxSize + " 字节。");
    }

    private byte[] resize(byte[] sourceBytes, int dimension) {
        try {
            var source = ImageIO.read(new ByteArrayInputStream(sourceBytes));
            if (source == null) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, "avatar-invalid-image", "头像不是可读取的图片。");
            }
            var resized = new BufferedImage(dimension, dimension, BufferedImage.TYPE_INT_ARGB);
            var graphics = resized.createGraphics();
            try {
                graphics.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                var edge = Math.min(source.getWidth(), source.getHeight());
                var x = (source.getWidth() - edge) / 2;
                var y = (source.getHeight() - edge) / 2;
                graphics.drawImage(source, 0, 0, dimension, dimension, x, y, x + edge, y + edge, null);
            } finally {
                graphics.dispose();
            }
            try (var output = new ByteArrayOutputStream()) {
                if (!ImageIO.write(resized, "png", output)) {
                    throw new ApiException(
                            HttpStatus.INTERNAL_SERVER_ERROR, "avatar-encode-failed", "PNG 编码不可用。");
                }
                return output.toByteArray();
            }
        } catch (IOException e) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "avatar-invalid-image", "无法读取头像。", e);
        }
    }

    private Mono<String> writeAvatar(User user, ParameterSetting.Session session, byte[] bytes) {
        return Mono.fromCallable(() -> {
                    var directory = directory(session);
                    Files.createDirectories(directory);
                    var filename = sanitize(user.getMetadata().getName()) + "-" + System.currentTimeMillis() + ".png";
                    var target = directory.resolve(filename).normalize();
                    if (!target.startsWith(directory)) {
                        throw new ApiException(
                                HttpStatus.BAD_REQUEST, "avatar-invalid-path", "不允许使用该头像路径。");
                    }
                    Files.write(target, bytes);
                    deleteAvatar(user.getSpec().getAvatar(), directory);
                    return AVATAR_URL_PREFIX + filename;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(
                        IOException.class,
                        error -> new ApiException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "avatar-storage-failed",
                                "无法存储头像。",
                                error));
    }

    private Path directory(ParameterSetting.Session session) {
        var workDir = pudansProperties.getWorkDir().toAbsolutePath().normalize();
        var configured = session.getAvatarStoragePath();
        var relative = StringUtils.hasText(configured) ? configured : DEFAULT_AVATAR_PATH;
        var resolved = workDir.resolve(relative).normalize();
        if (!resolved.startsWith(workDir)) {
            log.warn("忽略数据目录之外的头像存储路径：{}", configured);
            return workDir.resolve(DEFAULT_AVATAR_PATH);
        }
        return resolved;
    }

    private void deleteAvatar(String avatarUrl, Path directory) {
        if (!StringUtils.hasText(avatarUrl) || !avatarUrl.startsWith(AVATAR_URL_PREFIX)) {
            return;
        }
        var filename = avatarUrl.substring(AVATAR_URL_PREFIX.length());
        if (!isSafeFileName(filename)) {
            return;
        }
        var path = directory.resolve(filename).normalize();
        if (!path.startsWith(directory)) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("无法删除之前的头像 [{}]", path, e);
        }
    }

    private static boolean isSafeFileName(String filename) {
        return filename != null
                && filename.matches("[A-Za-z0-9._-]+")
                && ALLOWED_EXTENSIONS.contains(extension(filename));
    }

    private static String extension(String filename) {
        var dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String sanitize(String username) {
        return username.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}

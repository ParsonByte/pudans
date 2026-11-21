package cn.tolim.pudans.core.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.extension.api.Metadata;
import cn.tolim.pudans.infra.api.ParameterSetting;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.PudansProperties;
import cn.tolim.pudans.infra.setting.ParameterGroups;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AvatarStorageTest {

    @Mock
    ParameterGroups parameterGroups;

    @TempDir
    Path workDir;

    private AvatarStorage service;

    private PudansProperties properties;

    private ParameterSetting.Session session;

    @BeforeEach
    void setUp() {
        properties = new PudansProperties();
        properties.setWorkDir(workDir);
        session = new ParameterSetting.Session();
        session.setAvatarStoragePath("avatars");
        session.setAvatarMaxSize(1024 * 1024);
        session.setAvatarDimension(32);
        service = new AvatarStorage(parameterGroups, properties, new WebpSupport(true));
    }

    @Test
    void storesAResizedPngAndReturnsItsUrl() throws Exception {
        when(parameterGroups.getSession()).thenReturn(Mono.just(session));
        var user = user();

        var url = service.store(user, file(png(64, 64), MediaType.IMAGE_PNG)).block();

        assertThat(url).startsWith(AvatarStorage.AVATAR_URL_PREFIX + "alice-").endsWith(".png");
        var stored = workDir.resolve("avatars").resolve(url.substring(AvatarStorage.AVATAR_URL_PREFIX.length()));
        assertThat(Files.exists(stored)).isTrue();
        var image = ImageIO.read(stored.toFile());
        assertThat(image.getWidth()).isEqualTo(32);
        assertThat(image.getHeight()).isEqualTo(32);
    }

    @Test
    void deletesThePreviousAvatarWhenANewOneIsStored() throws Exception {
        when(parameterGroups.getSession()).thenReturn(Mono.just(session));
        var oldFile = workDir.resolve("avatars").resolve("alice-old.png");
        Files.createDirectories(oldFile.getParent());
        Files.write(oldFile, png(16, 16));
        var user = user();
        user.getSpec().setAvatar(AvatarStorage.AVATAR_URL_PREFIX + "alice-old.png");

        service.store(user, file(png(16, 16), MediaType.IMAGE_PNG)).block();

        assertThat(Files.exists(oldFile)).isFalse();
    }

    @Test
    void rejectsAFileAboveTheConfiguredLimit() throws Exception {
        session.setAvatarMaxSize(10);
        when(parameterGroups.getSession()).thenReturn(Mono.just(session));

        assertThatThrownBy(() -> service.store(user(), file(png(16, 16), MediaType.IMAGE_PNG)).block())
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("avatar-too-large"));
    }

    @Test
    void rejectsWebpWhenTheDecoderIsUnavailable() {
        service = new AvatarStorage(parameterGroups, properties, new WebpSupport(false));
        when(parameterGroups.getSession()).thenReturn(Mono.just(session));

        assertThatThrownBy(() -> service.store(user(), file("webp".getBytes(), MediaType.parseMediaType("image/webp"))).block())
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("avatar-webp-unavailable"));
    }

    @Test
    void rejectsAnUnsupportedContentType() {
        when(parameterGroups.getSession()).thenReturn(Mono.just(session));

        assertThatThrownBy(() -> service.store(user(), file("not an image".getBytes(), MediaType.TEXT_PLAIN)).block())
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("avatar-invalid-type"));
    }

    @Test
    void fallsBackToTheDataDirectoryWhenTheConfiguredPathEscapesIt() throws Exception {
        session.setAvatarStoragePath("../outside");
        when(parameterGroups.getSession()).thenReturn(Mono.just(session));

        var url = service.store(user(), file(png(16, 16), MediaType.IMAGE_PNG)).block();

        assertThat(url).startsWith(AvatarStorage.AVATAR_URL_PREFIX);
        assertThat(Files.exists(workDir.resolve("avatars").resolve(url.substring(AvatarStorage.AVATAR_URL_PREFIX.length()))))
                .isTrue();
        assertThat(Files.exists(workDir.getParent().resolve("outside"))).isFalse();
    }

    @Test
    void deletesTheCurrentAvatar() throws Exception {
        when(parameterGroups.getSession()).thenReturn(Mono.just(session));
        var avatar = workDir.resolve("avatars").resolve("alice-current.png");
        Files.createDirectories(avatar.getParent());
        Files.write(avatar, png(16, 16));
        var user = user();
        user.getSpec().setAvatar(AvatarStorage.AVATAR_URL_PREFIX + "alice-current.png");

        service.deleteForUser(user).block();

        assertThat(Files.exists(avatar)).isFalse();
    }

    private static User user() {
        var user = new User();
        var metadata = new Metadata();
        metadata.setName("alice");
        user.setMetadata(metadata);
        return user;
    }

    private static FilePart file(byte[] bytes, MediaType contentType) {
        var file = mock(FilePart.class);
        var headers = new HttpHeaders();
        headers.setContentType(contentType);
        headers.setContentLength(bytes.length);
        when(file.headers()).thenReturn(headers);
        lenient().when(file.content()).thenReturn(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(bytes)));
        return file;
    }

    private static byte[] png(int width, int height) throws Exception {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        try (var output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }
}

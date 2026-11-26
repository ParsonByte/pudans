package cn.tolim.pudans.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import cn.tolim.pudans.infra.contract.ExternalUrlSupplier;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.PudansProperties;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class SiteAssetsTest {

    @Mock
    ExternalUrlSupplier externalUrlSupplier;

    @TempDir
    Path workDir;

    private SiteAssets service;

    @BeforeEach
    void setUp() throws Exception {
        var properties = new PudansProperties();
        properties.setWorkDir(workDir);
        service = new SiteAssets(properties, externalUrlSupplier);
        lenient()
                .when(externalUrlSupplier.getURL(any()))
                .thenReturn(URI.create("https://pudans.example.com").toURL());
    }

    @Test
    void storesAPngAndReturnsAnAbsoluteUrl() throws Exception {
        var bytes = png();

        var url = service.store("logoDark", file(bytes, MediaType.IMAGE_PNG), request()).block();

        assertThat(url)
                .startsWith("https://pudans.example.com" + SiteAssets.SITE_ASSET_URL_PREFIX + "logoDark-")
                .endsWith(".png");
        var stored = storedFile(url);
        assertThat(Files.readAllBytes(stored)).isEqualTo(bytes);
    }

    @Test
    void storesAnSvgAndAnIco() throws Exception {
        var svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes(StandardCharsets.UTF_8);
        var ico = new byte[] {0, 0, 1, 0, 0, 0};

        var svgUrl = service.store("favicon", file(svg, MediaType.parseMediaType("image/svg+xml")), request())
                .block();
        var icoUrl = service.store("iconMedium", file(ico, MediaType.parseMediaType("image/x-icon")), request())
                .block();

        assertThat(svgUrl).endsWith(".svg");
        assertThat(icoUrl).endsWith(".ico");
        assertThat(Files.exists(storedFile(svgUrl))).isTrue();
        assertThat(Files.exists(storedFile(icoUrl))).isTrue();
    }

    @Test
    void rejectsAnUnsupportedField() throws Exception {
        assertThatThrownBy(() -> service.store("avatar", file(png(), MediaType.IMAGE_PNG), request())
                        .block())
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("site-asset-invalid-field"));
    }

    @Test
    void rejectsAnUnsupportedContentType() throws Exception {
        assertThatThrownBy(() -> service.store(
                                "logoLight", file("plain".getBytes(), MediaType.TEXT_PLAIN), request())
                        .block())
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("site-asset-invalid-type"));
    }

    @Test
    void rejectsContentThatDoesNotMatchTheDeclaredType() throws Exception {
        assertThatThrownBy(() -> service.store("logoLight", file(png(), MediaType.IMAGE_JPEG), request())
                        .block())
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("site-asset-invalid-image"));
    }

    @Test
    void rejectsAFileAboveTheSizeLimit() throws Exception {
        var bytes = png();

        assertThatThrownBy(() -> service.store(
                                "logoDark", file(bytes, MediaType.IMAGE_PNG, 2L * 1024 * 1024 + 1), request())
                        .block())
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("site-asset-too-large"));
    }

    @Test
    void rejectsAnSvgWithScripts() throws Exception {
        var svg = ("<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>")
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.store(
                                "favicon", file(svg, MediaType.parseMediaType("image/svg+xml")), request())
                        .block())
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("site-asset-svg-unsafe"));
    }

    @Test
    void loadsAStoredAssetWithItsMediaType() throws Exception {
        var url = service.store("favicon", file(ico(), MediaType.parseMediaType("image/x-icon")), request())
                .block();
        var filename = filenameOf(url);

        var asset = service.load(filename).block();

        assertThat(asset).isNotNull();
        assertThat(asset.mediaType()).isEqualTo(MediaType.parseMediaType("image/x-icon"));
        assertThat(asset.resource().exists()).isTrue();
    }

    @Test
    void rejectsAnUnsafeFilename() {
        assertThatThrownBy(() -> service.load("../logoDark.png").block())
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("site-asset-not-found"));
    }

    @Test
    void reportsAMissingAssetAsNotFound() throws Exception {
        var url = service.store("logoDark", file(png(), MediaType.IMAGE_PNG), request())
                .block();
        var filename = filenameOf(url);
        Files.delete(storedFile(url));

        assertThatThrownBy(() -> service.load(filename).block())
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("site-asset-not-found"));
    }

    private Path storedFile(String url) {
        var filename = filenameOf(url);
        return workDir.resolve(SiteAssets.DEFAULT_SITE_ASSET_PATH).resolve(filename);
    }

    private static String filenameOf(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private static HttpRequest request() {
        return mock(HttpRequest.class);
    }

    private static FilePart file(byte[] bytes, MediaType contentType) {
        return file(bytes, contentType, bytes.length);
    }

    private static FilePart file(byte[] bytes, MediaType contentType, long declaredLength) {
        var file = mock(FilePart.class);
        var headers = new HttpHeaders();
        headers.setContentType(contentType);
        headers.setContentLength(declaredLength);
        lenient().when(file.headers()).thenReturn(headers);
        lenient().when(file.content()).thenReturn(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(bytes)));
        return file;    }

    private static byte[] png() throws Exception {
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        try (var output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }

    private static byte[] ico() {
        return new byte[] {0, 0, 1, 0, 0, 0};
    }
}
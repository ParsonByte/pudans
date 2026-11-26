package cn.tolim.pudans.setting.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.setting.SiteAssets;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class SiteAssetResourceEndpointTest {

    @Mock
    SiteAssets siteAssets;

    @TempDir
    Path workDir;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        var endpoint = new SiteAssetResourceEndpoint(siteAssets);
        webTestClient = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void servesAPngWithLongLivedCaching() throws Exception {
        var file = Files.write(workDir.resolve("logoDark-abc.png"), new byte[] {1, 2, 3});
        when(siteAssets.load("logoDark-abc.png"))
                .thenReturn(Mono.just(new SiteAssets.SiteAsset(new FileSystemResource(file), MediaType.IMAGE_PNG)));

        webTestClient
                .get()
                .uri("/site-assets/logoDark-abc.png")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.IMAGE_PNG)
                .expectHeader()
                .valueEquals("Cache-Control", "public, max-age=31536000, immutable")
                .expectHeader()
                .valueEquals("X-Content-Type-Options", "nosniff")
                .expectBody()
                .consumeWith(result -> assertThat(result.getResponseBody()).isEqualTo(new byte[] {1, 2, 3}));
    }

    @Test
    void servesAnSvgWithASandboxPolicy() throws Exception {
        var file = Files.write(workDir.resolve("favicon-abc.svg"), "<svg/>".getBytes(StandardCharsets.UTF_8));
        when(siteAssets.load("favicon-abc.svg"))
                .thenReturn(Mono.just(new SiteAssets.SiteAsset(
                        new FileSystemResource(file), MediaType.parseMediaType("image/svg+xml"))));

        webTestClient
                .get()
                .uri("/site-assets/favicon-abc.svg")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.parseMediaType("image/svg+xml"))
                .expectHeader()
                .valueEquals("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox");
    }

    @Test
    void reportsAMissingAssetAsNotFound() {
        when(siteAssets.load("missing.png"))
                .thenReturn(Mono.error(
                        new ApiException(HttpStatus.NOT_FOUND, "site-asset-not-found", "站点图片不存在。")));

        webTestClient.get().uri("/site-assets/missing.png").exchange().expectStatus().isNotFound();
    }
}
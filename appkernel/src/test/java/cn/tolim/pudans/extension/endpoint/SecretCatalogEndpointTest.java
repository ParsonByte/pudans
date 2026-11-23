package cn.tolim.pudans.extension.endpoint;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.extension.SecretDirectory;
import cn.tolim.pudans.extension.contract.SecretCatalog;
import cn.tolim.pudans.extension.contract.SecretCatalogSummary;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class SecretCatalogEndpointTest {

    @Mock
    SecretDirectory directory;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        var endpoint = new SecretCatalogEndpoint(directory);
        webTestClient = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    private static SecretCatalog catalog(int page, int size) {
        return new SecretCatalog(
                page,
                size,
                0,
                0,
                Instant.parse("2026-11-23T04:00:00Z"),
                new SecretCatalogSummary(0, 0, 0, 0, List.of()),
                List.of());
    }

    @Test
    void defaultsToTheFirstPageWithTwentyItems() {
        when(directory.catalog(null, null, 1, 20)).thenReturn(Mono.just(catalog(1, 20)));

        webTestClient
                .get()
                .uri("/secrets/catalog")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.page")
                .isEqualTo(1)
                .jsonPath("$.size")
                .isEqualTo(20)
                .jsonPath("$.summary.total")
                .isEqualTo(0)
                .jsonPath("$.items")
                .isEmpty();

        verify(directory).catalog(null, null, 1, 20);
    }

    @Test
    void passesKeywordTypeAndPaginationToTheDirectory() {
        when(directory.catalog("token", "Opaque", 2, 5)).thenReturn(Mono.just(catalog(2, 5)));

        webTestClient
                .get()
                .uri("/secrets/catalog?page=2&size=5&keyword=token&type=Opaque")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.page")
                .isEqualTo(2)
                .jsonPath("$.size")
                .isEqualTo(5);

        verify(directory).catalog("token", "Opaque", 2, 5);
    }

    @Test
    void rejectsInvalidPaginationBeforeLoadingTheCatalog() {
        for (var query : List.of(
                "page=0", "page=-1", "page=abc", "size=0", "size=-1", "size=101", "size=abc")) {
            webTestClient
                    .get()
                    .uri("/secrets/catalog?" + query)
                    .exchange()
                    .expectStatus()
                    .isBadRequest();
        }

        verifyNoInteractions(directory);
    }
}

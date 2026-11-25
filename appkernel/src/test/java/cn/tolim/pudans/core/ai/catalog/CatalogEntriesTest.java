package cn.tolim.pudans.core.ai.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.dto.CatalogEntryCreateRequest;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.catalog.CatalogEntryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class CatalogEntriesTest {

    private static final Instant NOW = Instant.parse("2026-11-25T02:00:00Z");

    @Mock
    CatalogEntryRepository entries;

    private CatalogEntries service;

    @BeforeEach
    void setUp() {
        service = new CatalogEntries(entries);
        service.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsAValidCatalogEntry() {
        when(entries.insert(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        var result = service.create(new CatalogEntryCreateRequest(
                        "creation_media_mode",
                        "image",
                        "图片",
                        null,
                        null,
                        10,
                        true,
                        Map.of("layout", "grid"),
                        null,
                        null))
                .block();

        assertThat(result).isNotNull();
        assertThat(result.catalogKind()).isEqualTo("creation_media_mode");
        assertThat(result.entryKey()).isEqualTo("image");
        assertThat(result.metadata()).containsEntry("layout", "grid");
    }

    @Test
    void rejectsAnUnregisteredCatalogKind() {
        StepVerifier.create(service.create(new CatalogEntryCreateRequest(
                        "unknown_kind",
                        "image",
                        "图片",
                        null,
                        null,
                        null,
                        true,
                        null,
                        null,
                        null)))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> assertThat(api.getCode())
                                .isEqualTo("catalog-entry-kind-invalid")))
                .verify();
        verify(entries, never()).insert(any());
    }

    @Test
    void rejectsAnInvalidValidityWindow() {
        StepVerifier.create(service.create(new CatalogEntryCreateRequest(
                        "asset_kind",
                        "image",
                        "图片",
                        null,
                        null,
                        null,
                        true,
                        null,
                        NOW,
                        NOW.minusSeconds(60))))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> assertThat(api.getCode())
                                .isEqualTo("catalog-entry-validity-invalid")))
                .verify();
    }
}

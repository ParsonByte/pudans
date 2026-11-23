package cn.tolim.pudans.extension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.Secret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class SecretDirectoryTest {

    private static final Instant NOW = Instant.now();

    @Mock
    ReactiveExtensionClient client;

    private SecretDirectory directory;

    @BeforeEach
    void setUp() {
        directory = new SecretDirectory(client);
    }

    private static Secret secret(
            String name,
            String type,
            Map<String, String> data,
            Map<String, String> stringData,
            Instant createdAt) {
        var secret = new Secret();
        var metadata = new Metadata();
        metadata.setName(name);
        metadata.setLabels(Map.of("app", "console"));
        metadata.setAnnotations(Map.of("owner", "security"));
        metadata.setCreationTimestamp(createdAt);
        metadata.setVersion(3L);
        secret.setMetadata(metadata);
        secret.setType(type);
        secret.setData(data == null
                ? null
                : data.entrySet()
                        .stream()
                        .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> entry.getValue().getBytes(StandardCharsets.UTF_8))));
        secret.setStringData(stringData);
        return secret;
    }

    private void givenSecrets(Secret... secrets) {
        when(client.listAll(eq(Secret.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.fromArray(secrets));
    }

    @Test
    void valuesDoNotEnterTheCatalogOrItsSerializedResult() throws Exception {
        givenSecrets(secret(
                "alpha",
                null,
                Map.of("TOKEN", "hidden-data-value"),
                Map.of("password", "hidden-string-value"),
                NOW));

        var catalog = directory.catalog(null, null, 1, 20).block();

        assertThat(catalog).isNotNull();
        var item = catalog.items().getFirst();
        assertThat(item.name()).isEqualTo("alpha");
        assertThat(item.type()).isEqualTo("Opaque");
        assertThat(item.keys()).containsExactly("TOKEN", "password");
        assertThat(item.dataKeys()).containsExactly("TOKEN");
        assertThat(item.stringDataKeys()).containsExactly("password");
        assertThat(item.hasData()).isTrue();
        assertThat(item.hasStringData()).isTrue();
        assertThat(item.labels()).containsEntry("app", "console");
        assertThat(item.annotations()).containsEntry("owner", "security");
        assertThat(item.creationTimestamp()).isEqualTo(NOW);
        assertThat(item.version()).isEqualTo(3L);

        var json = JsonMapper.shared().writeValueAsString(catalog);

        assertThat(json)
                .doesNotContain("hidden-data-value")
                .doesNotContain("hidden-string-value")
                .doesNotContain("\"data\"")
                .doesNotContain("\"stringData\"");
    }

    @Test
    void keywordMatchesNamesAndKeysCaseInsensitivelyButNotValues() {
        givenSecrets(
                secret(
                        "alpha",
                        "Opaque",
                        Map.of("TOKEN", "one"),
                        Map.of("password", "two"),
                        NOW),
                secret(
                        "Needle",
                        "Opaque",
                        Map.of("signature", "three"),
                        Map.of("private-key", "four"),
                        NOW),
                secret(
                        "gamma",
                        "Opaque",
                        Map.of("unrelated", "needle-value"),
                        Map.of(),
                        NOW));

        assertThat(directory.catalog("tOkEn", null, 1, 20).block().items())
                .extracting(item -> item.name())
                .containsExactly("alpha");
        assertThat(directory.catalog("NEED", null, 1, 20).block().items())
                .extracting(item -> item.name())
                .containsExactly("Needle");
        assertThat(directory.catalog("private-", null, 1, 20).block().items())
                .extracting(item -> item.name())
                .containsExactly("Needle");
    }

    @Test
    void typeFilterMatchesExactlyAndCountsAnEmptyTypeAsOpaque() {
        givenSecrets(
                secret("opaque", null, Map.of("a", "one"), Map.of(), NOW),
                secret("tls", "kubernetes.io/tls", Map.of("tls.crt", "two"), Map.of(), NOW));

        assertThat(directory.catalog(null, "Opaque", 1, 20).block().items())
                .extracting(item -> item.name())
                .containsExactly("opaque");
        assertThat(directory.catalog(null, "opaque", 1, 20).block().items())
                .isEmpty();
        assertThat(directory.catalog(null, "kubernetes.io/tls", 1, 20).block().items())
                .extracting(item -> item.name())
                .containsExactly("tls");
    }

    @Test
    void summaryUsesAllSecretsBeforeFilters() {
        givenSecrets(
                secret(
                        "one",
                        "Alpha",
                        Map.of("a", "one", "b", "two"),
                        Map.of("c", "three"),
                        NOW.minusSeconds(60)),
                secret("two", "Beta", null, null, NOW.minusSeconds(60)),
                secret(
                        "three",
                        "Alpha",
                        Map.of("d", "four"),
                        Map.of(),
                        NOW.minusSeconds(8 * 24 * 60 * 60)),
                secret(
                        "four",
                        "Gamma",
                        Map.of("e", "five", "f", "six"),
                        Map.of(),
                        NOW.minusSeconds(60)));

        var catalog = directory.catalog("missing", null, 1, 20).block();

        assertThat(catalog).isNotNull();
        assertThat(catalog.items()).isEmpty();
        assertThat(catalog.total()).isZero();
        assertThat(catalog.totalPages()).isZero();
        assertThat(catalog.summary().total()).isEqualTo(4);
        assertThat(catalog.summary().entries()).isEqualTo(6);
        assertThat(catalog.summary().empty()).isEqualTo(1);
        assertThat(catalog.summary().createdLastSevenDays()).isEqualTo(3);
        assertThat(catalog.summary().types())
                .extracting(count -> count.type(), count -> count.count(), count -> count.entries())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Alpha", 2L, 4L),
                        org.assertj.core.groups.Tuple.tuple("Beta", 1L, 0L),
                        org.assertj.core.groups.Tuple.tuple("Gamma", 1L, 2L));
    }

    @Test
    void paginationReturnsEmptyItemsBeyondTheLastPage() {
        givenSecrets(
                secret("one", "Opaque", Map.of("a", "one"), Map.of(), NOW),
                secret("two", "Opaque", Map.of("b", "two"), Map.of(), NOW),
                secret("three", "Opaque", Map.of("c", "three"), Map.of(), NOW),
                secret("four", "Opaque", Map.of("d", "four"), Map.of(), NOW),
                secret("five", "Opaque", Map.of("e", "five"), Map.of(), NOW));

        var secondPage = directory.catalog(null, null, 2, 2).block();
        var lastPage = directory.catalog(null, null, 3, 2).block();
        var beyond = directory.catalog(null, null, 4, 2).block();
        var oversized = directory.catalog(null, null, 1, 101).block();

        assertThat(secondPage.page()).isEqualTo(2);
        assertThat(secondPage.size()).isEqualTo(2);
        assertThat(secondPage.total()).isEqualTo(5);
        assertThat(secondPage.totalPages()).isEqualTo(3);
        assertThat(secondPage.items()).extracting(item -> item.name()).containsExactly("three", "four");
        assertThat(lastPage.items()).extracting(item -> item.name()).containsExactly("five");
        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.total()).isEqualTo(5);
        assertThat(beyond.totalPages()).isEqualTo(3);
        assertThat(oversized.page()).isEqualTo(1);
        assertThat(oversized.size()).isEqualTo(100);
    }
}

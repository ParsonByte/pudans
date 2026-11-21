package cn.tolim.pudans.core.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.extension.contract.ConfigMap;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.infra.contract.ParameterSetting;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import cn.tolim.pudans.infra.setting.ParameterGroups;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AnnouncementReadsTest {

    @Mock
    ParameterGroups parameterGroups;

    @Mock
    ReactiveExtensionClient client;

    private AnnouncementReads service;

    @BeforeEach
    void setUp() {
        service = new AnnouncementReads(parameterGroups, client);
        service.setClock(java.time.Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneOffset.UTC));
    }

    @Test
    void anUndismissedAnnouncementIsReportedAsNotDismissed() {
        when(parameterGroups.getSite()).thenReturn(Mono.just(site("v1")));
        when(client.fetch(ConfigMap.class, "announcement-state-alice")).thenReturn(Mono.empty());

        StepVerifier.create(service.get("alice"))
                .assertNext(state -> {
                    assertThat(state.version()).isEqualTo("v1");
                    assertThat(state.dismissed()).isFalse();
                    assertThat(state.dismissedAt()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void dismissCreatesTheStateConfigMap() {
        when(parameterGroups.getSite()).thenReturn(Mono.just(site("v1")));
        when(client.fetch(ConfigMap.class, "announcement-state-alice")).thenReturn(Mono.empty());
        when(client.create(any(ConfigMap.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.dismiss("alice"))
                .assertNext(state -> {
                    assertThat(state.version()).isEqualTo("v1");
                    assertThat(state.dismissed()).isTrue();
                    assertThat(state.dismissedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
                })
                .verifyComplete();

        verify(client).create(any(ConfigMap.class));
    }

    @Test
    void aDismissedVersionIsReportedAsDismissed() {
        when(parameterGroups.getSite()).thenReturn(Mono.just(site("v1")));
        when(client.fetch(ConfigMap.class, "announcement-state-alice"))
                .thenReturn(Mono.just(stateConfigMap("v1", Instant.parse("2026-01-01T00:00:00Z"))));

        StepVerifier.create(service.get("alice"))
                .assertNext(state -> {
                    assertThat(state.dismissed()).isTrue();
                    assertThat(state.dismissedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
                })
                .verifyComplete();
    }

    @Test
    void aNewAnnouncementVersionMakesTheOldDismissalStale() {
        when(parameterGroups.getSite()).thenReturn(Mono.just(site("v2")));
        when(client.fetch(ConfigMap.class, "announcement-state-alice"))
                .thenReturn(Mono.just(stateConfigMap("v1", Instant.parse("2026-01-01T00:00:00Z"))));

        StepVerifier.create(service.get("alice"))
                .assertNext(state -> {
                    assertThat(state.version()).isEqualTo("v2");
                    assertThat(state.dismissed()).isFalse();
                    assertThat(state.dismissedAt()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void deletingAUserRemovesTheAnnouncementState() {
        var configMap = stateConfigMap("v1", Instant.parse("2026-01-01T00:00:00Z"));
        when(client.fetch(ConfigMap.class, "announcement-state-alice")).thenReturn(Mono.just(configMap));
        when(client.delete(configMap)).thenReturn(Mono.just(configMap));

        service.deleteForUser("alice").block();

        verify(client).delete(configMap);
    }

    private static ParameterSetting.Site site(String version) {
        var site = new ParameterSetting.Site();
        site.setAnnouncementVersion(version);
        return site;
    }

    private static ConfigMap stateConfigMap(String version, Instant dismissedAt) {
        var state = new AnnouncementReads.StoredState();
        state.setVersion(version);
        state.setDismissedAt(dismissedAt);
        var configMap = new ConfigMap();
        var metadata = new Metadata();
        metadata.setName("announcement-state-alice");
        configMap.setMetadata(metadata);
        configMap.setData(Map.of("state", JsonUtils.objectToJson(state)));
        return configMap;
    }
}

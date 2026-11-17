package cn.tolim.pudans.core.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.extension.ConfigMap;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.infra.ParameterSetting;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.infra.setting.ParameterSettingGroupService;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AnnouncementStateServiceTest {

    @Mock
    ParameterSettingGroupService parameterSettingGroupService;

    @Mock
    ReactiveExtensionClient client;

    private AnnouncementStateService service;

    @BeforeEach
    void setUp() {
        service = new AnnouncementStateService(parameterSettingGroupService, client);
        service.setClock(java.time.Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneOffset.UTC));
    }

    @Test
    void anUndismissedAnnouncementIsReportedAsNotDismissed() {
        when(parameterSettingGroupService.getSite()).thenReturn(Mono.just(site("v1")));
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
        when(parameterSettingGroupService.getSite()).thenReturn(Mono.just(site("v1")));
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
        when(parameterSettingGroupService.getSite()).thenReturn(Mono.just(site("v1")));
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
        when(parameterSettingGroupService.getSite()).thenReturn(Mono.just(site("v2")));
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
        var state = new AnnouncementStateService.StoredState();
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

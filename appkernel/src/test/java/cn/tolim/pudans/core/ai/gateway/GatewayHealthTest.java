package cn.tolim.pudans.core.ai.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.ai.AiRouteGroup;
import cn.tolim.pudans.contract.ai.AiSetting;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.infra.SystemVersionSupplier;
import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.core.infra.properties.AiProperties;
import cn.tolim.pudans.core.infra.r2dbc.ai.chat.AiChatRequestRepository;
import com.github.zafarkhaja.semver.Version;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class GatewayHealthTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    AiSettings aiSettings;

    @Mock
    AiChatRequestRepository chatRequestRepository;

    @Mock
    SystemVersionSupplier versionSupplier;

    private GatewayHealth service;

    private AiProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        properties.getRuntime().setGatewayUrl("http://gateway.local/apis/aigateway");
        service = new GatewayHealth(client, aiSettings, chatRequestRepository, properties, versionSupplier);
    }

    @Test
    void reportsEnabledModelsGroupsAndRecentErrors() {
        var setting = new AiSetting.Spec();
        setting.setDefaultRouteGroup("group-b");
        when(aiSettings.spec()).thenReturn(Mono.just(setting));
        when(client.listAll(eq(AiRouteGroup.class), any(), any()))
                .thenReturn(Flux.just(group("group-a", false), group("group-b", true)));
        when(client.listAll(eq(AiModel.class), any(), any()))
                .thenReturn(Flux.just(model("gpt-b"), model("gpt-a"), model("gpt-a")));
        when(chatRequestRepository.countRecentFailures(any())).thenReturn(Mono.just(3L));
        when(versionSupplier.get()).thenReturn(Version.parse("1.2.3"));

        StepVerifier.create(service.status())
                .assertNext(status -> {
                    assertThat(status.enabled()).isTrue();
                    assertThat(status.entryUrl()).isEqualTo("http://gateway.local/apis/aigateway");
                    assertThat(status.protocols())
                            .containsExactly(AiProvider.Protocol.OPENAI_CHAT, AiProvider.Protocol.ANTHROPIC);
                    assertThat(status.models()).containsExactly("gpt-a", "gpt-b");
                    assertThat(status.routeGroups()).containsExactly("group-a", "group-b");
                    assertThat(status.defaultRouteGroup()).isEqualTo("group-b");
                    assertThat(status.rateLimitEnabled()).isTrue();
                    assertThat(status.recentErrors()).isEqualTo(3);
                    assertThat(status.version()).isEqualTo("1.2.3");
                })
                .verifyComplete();
    }

    @Test
    void fallsBackToTheDefaultRouteGroupWhenSettingHasNone() {
        var setting = new AiSetting.Spec();
        setting.setDefaultRouteGroup("");
        when(aiSettings.spec()).thenReturn(Mono.just(setting));
        when(client.listAll(eq(AiRouteGroup.class), any(), any()))
                .thenReturn(Flux.just(group("group-a", false), group("group-b", true)));
        when(client.listAll(eq(AiModel.class), any(), any())).thenReturn(Flux.empty());
        when(chatRequestRepository.countRecentFailures(any())).thenReturn(Mono.just(0L));
        when(versionSupplier.get()).thenReturn(null);

        StepVerifier.create(service.status())
                .assertNext(status -> {
                    assertThat(status.defaultRouteGroup()).isEqualTo("group-b");
                    assertThat(status.models()).isEmpty();
                    assertThat(status.version()).isNull();
                })
                .verifyComplete();
    }

    private static AiRouteGroup group(String name, boolean isDefault) {
        var group = new AiRouteGroup();
        var metadata = new Metadata();
        metadata.setName(name);
        group.setMetadata(metadata);
        group.getSpec().setDisplayName(name);
        group.getSpec().setDefault(isDefault);
        return group;
    }

    private static AiModel model(String code) {
        var model = new AiModel();
        var metadata = new Metadata();
        metadata.setName("model-" + code);
        model.setMetadata(metadata);
        model.getSpec().setProvider("provider-a");
        model.getSpec().setModelCode(code);
        return model;
    }
}
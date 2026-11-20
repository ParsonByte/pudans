package cn.tolim.pudans.contract.core.extension.ai;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.extension.Unstructured;
import cn.tolim.pudans.infra.utils.YamlUnstructuredLoader;
import java.io.IOException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class AiConfigTemplatesTest {

    private static final String CONFIG_LOCATION = "classpath:/extensions/config/config-ai-*.yaml";

    @Test
    void loadsAndMapsAiConfigTemplates() throws IOException {
        var resources = new PathMatchingResourcePatternResolver().getResources(CONFIG_LOCATION);
        assertThat(resources).hasSize(8);

        var documents = new YamlUnstructuredLoader(resources).load();
        assertThat(documents).hasSize(8);
        var byName = documents.stream()
                .collect(Collectors.toMap(
                        document -> document.getKind() + "|" + document.getMetadata().getName(),
                        Function.identity()));

        var setting = Unstructured.OBJECT_MAPPER.convertValue(
                byName.get("AiSetting|ai-setting").getData(), AiSetting.class);
        assertThat(setting.getSpec().getDefaults().getTeam()).isEqualTo("default");
        assertThat(setting.getSpec().getDefaults().getAgent()).isEqualTo("default");
        assertThat(setting.getSpec().getGateway().getSupportedProtocols())
                .containsExactly(AiProvider.Protocol.OPENAI_CHAT, AiProvider.Protocol.ANTHROPIC);
        assertThat(setting.getSpec().getGateway().isRateLimitEnabled()).isTrue();
        assertThat(setting.getSpec().getGateway().getMaxRetries()).isEqualTo(2);
        assertThat(setting.getSpec().getGateway().getRetryBackoffMs()).isEqualTo(200);
        assertThat(setting.getSpec().getGateway().getRetryOnStatuses())
                .containsExactly(429, 500, 502, 503, 504);
        assertThat(setting.getSpec().getGateway().getRequestTimeoutSeconds()).isEqualTo(120);
        assertThat(setting.getSpec().getEmbedding().getDimension()).isEqualTo(1536);
        assertThat(setting.getSpec().getMemory().getOrgPromotionThreshold()).isEqualTo(3);

        var fastMode = Unstructured.OBJECT_MAPPER.convertValue(
                byName.get("AiChatMode|fast").getData(), AiChatMode.class);
        assertThat(fastMode.getSpec().getRouteGroup()).isEqualTo("fast-group");
        assertThat(fastMode.getSpec().getDefaultTier()).isEqualTo(AiModel.Tier.CHEAP);

        var proMode = Unstructured.OBJECT_MAPPER.convertValue(
                byName.get("AiChatMode|pro").getData(), AiChatMode.class);
        assertThat(proMode.getSpec().getRouteGroup()).isEqualTo("pro-group");
        assertThat(proMode.getSpec().getDefaultTier()).isEqualTo(AiModel.Tier.AUTO);

        var defaultGroup = Unstructured.OBJECT_MAPPER.convertValue(
                byName.get("AiRouteGroup|default").getData(), AiRouteGroup.class);
        assertThat(defaultGroup.getSpec().isDefault()).isTrue();
        assertThat(defaultGroup.getSpec().getRouteMode()).isEqualTo(AiRouteGroup.RouteMode.ORDER);
        assertThat(defaultGroup.effectiveParentMode()).isEqualTo(AiRouteGroup.ParentMode.NONE);

        var fastGroup = Unstructured.OBJECT_MAPPER.convertValue(
                byName.get("AiRouteGroup|fast-group").getData(), AiRouteGroup.class);
        assertThat(fastGroup.getSpec().getParentMode()).isEqualTo(AiRouteGroup.ParentMode.FAST);
        assertThat(fastGroup.effectiveParentMode()).isEqualTo(AiRouteGroup.ParentMode.FAST);

        var proGroup = Unstructured.OBJECT_MAPPER.convertValue(
                byName.get("AiRouteGroup|pro-group").getData(), AiRouteGroup.class);
        assertThat(proGroup.getSpec().getParentMode()).isEqualTo(AiRouteGroup.ParentMode.PRO);
        assertThat(proGroup.effectiveParentMode()).isEqualTo(AiRouteGroup.ParentMode.PRO);

        var team = Unstructured.OBJECT_MAPPER.convertValue(
                byName.get("AiTeam|default").getData(), AiTeam.class);
        assertThat(team.getSpec().getDefaultAgent()).isEqualTo("default");
        assertThat(team.getSpec().getMembers()).hasSize(1);

        var agent = Unstructured.OBJECT_MAPPER.convertValue(
                byName.get("AiAgent|default").getData(), AiAgent.class);
        assertThat(agent.getSpec().getTeam()).isEqualTo("default");
        assertThat(agent.getSpec().getDefaultRouteGroup()).isEqualTo("default");
    }
}

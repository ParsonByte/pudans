package cn.tolim.pudans.contract.ai.resource;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.extension.Scheme;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiExtensionSchemeTest {

    @Test
    void buildsSchemesForEveryAiResource() {
        var schemes = List.of(
                Scheme.buildFromType(AiProvider.class),
                Scheme.buildFromType(AiProviderAccount.class),
                Scheme.buildFromType(AiModel.class),
                Scheme.buildFromType(AiRouteGroup.class),
                Scheme.buildFromType(AiChatMode.class),
                Scheme.buildFromType(AiSetting.class),
                Scheme.buildFromType(AiTeam.class),
                Scheme.buildFromType(AiAgent.class),
                Scheme.buildFromType(AiMemoryPrompt.class),
                Scheme.buildFromType(AiMemoryPromptBinding.class),
                Scheme.buildFromType(KnowledgeBase.class));

        assertThat(schemes).hasSize(11);
        assertThat(schemes)
                .extracting(scheme -> scheme.groupVersionKind().kind())
                .containsExactly(
                        "AiProvider",
                        "AiProviderAccount",
                        "AiModel",
                        "AiRouteGroup",
                        "AiChatMode",
                        "AiSetting",
                        "AiTeam",
                        "AiAgent",
                        "AiMemoryPrompt",
                        "AiMemoryPromptBinding",
                        "KnowledgeBase");
        assertThat(schemes).allSatisfy(scheme -> assertThat(scheme.openApiSchema()).isNotNull());
    }
}

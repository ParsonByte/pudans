package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import java.util.List;
import java.util.Map;

final class AiProviderAdapterTestFixtures {

    private AiProviderAdapterTestFixtures() {}

    static AiProvider provider(AiProvider.Protocol protocol) {
        var provider = new AiProvider();
        provider.getSpec().setDisplayName("upstream");
        provider.getSpec().setProtocol(protocol);
        provider.getSpec().setBaseUrl("https://api.example.com");
        return provider;
    }

    static AiProviderAccount account() {
        var account = new AiProviderAccount();
        account.getSpec().setProvider("upstream");
        return account;
    }

    static AiModel model(String modelCode, Integer maxOutputTokens) {
        var model = new AiModel();
        model.getSpec().setProvider("upstream");
        model.getSpec().setModelCode(modelCode);
        model.getSpec().setMaxOutputTokens(maxOutputTokens);
        return model;
    }

    static AiChatIr request() {
        return new AiChatIr(
                List.of(new AiChatMessage("user", List.of(new AiContentPart.Text("hello", null)))),
                List.of(),
                new AiRequestParams("requested-model", 0.2, null, null, null, List.of(), null, Map.of()),
                false);
    }
}

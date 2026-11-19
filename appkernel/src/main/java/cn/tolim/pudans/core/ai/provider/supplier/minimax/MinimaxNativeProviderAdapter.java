package cn.tolim.pudans.core.ai.provider.supplier.minimax;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.core.ai.provider.AiProviderAccountService;
import cn.tolim.pudans.core.ai.provider.AiWebClientConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class MinimaxNativeProviderAdapter extends MinimaxChatProviderAdapter {

    public MinimaxNativeProviderAdapter(
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient,
            ObjectMapper mapper,
            AiProviderAccountService accountService) {
        super(webClient, mapper, accountService);
    }

    @Override
    public AiProvider.Protocol protocol() {
        return AiProvider.Protocol.MINIMAX_NATIVE;
    }

    @Override
    protected String path() {
        return MinimaxProtocol.NATIVE_CHAT_PATH;
    }
}

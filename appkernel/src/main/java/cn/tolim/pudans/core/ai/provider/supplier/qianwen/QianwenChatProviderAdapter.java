package cn.tolim.pudans.core.ai.provider.supplier.qianwen;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.core.ai.provider.AiProviderAccountService;
import cn.tolim.pudans.core.ai.provider.AiWebClientConfiguration;
import cn.tolim.pudans.core.ai.provider.OpenAiChatProviderAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class QianwenChatProviderAdapter extends OpenAiChatProviderAdapter {

    public QianwenChatProviderAdapter(
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient,
            ObjectMapper mapper,
            AiProviderAccountService accountService) {
        super(webClient, mapper, accountService);
    }

    @Override
    public AiProvider.Protocol protocol() {
        return AiProvider.Protocol.QIANWEN;
    }

    @Override
    protected String path() {
        return QianwenProtocol.CHAT_PATH;
    }
}

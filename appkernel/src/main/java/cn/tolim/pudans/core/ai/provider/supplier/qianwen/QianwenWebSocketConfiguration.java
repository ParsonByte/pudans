package cn.tolim.pudans.core.ai.provider.supplier.qianwen;

import cn.tolim.pudans.extension.api.ApiGroups;
import cn.tolim.pudans.core.ai.provider.ProviderAccounts;
import cn.tolim.pudans.core.ai.provider.supplier.SupplierWebSocketHandler;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;

@Configuration
public class QianwenWebSocketConfiguration {

    @Bean
    SupplierWebSocketHandler qianwenWebSocketHandler(
            QianwenProviderAccess access, ProviderAccounts providerAccounts) {
        return new SupplierWebSocketHandler(
                "qianwen", access, providerAccounts, QianwenProtocol::normalizeWebSocketPath);
    }

    @Bean
    HandlerMapping qianwenWebSocketHandlerMapping(
            @Qualifier("qianwenWebSocketHandler") SupplierWebSocketHandler handler) {
        var mapping = new SimpleUrlHandlerMapping();
        mapping.setOrder(-1);
        mapping.setUrlMap(Map.of(
                ApiGroups.API_PREFIX + "/aiproviders/*/qianwen/ws/**", handler));
        return mapping;
    }
}

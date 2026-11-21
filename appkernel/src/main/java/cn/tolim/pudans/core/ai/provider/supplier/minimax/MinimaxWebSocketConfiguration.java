package cn.tolim.pudans.core.ai.provider.supplier.minimax;

import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.core.ai.provider.ProviderAccounts;
import cn.tolim.pudans.core.ai.provider.supplier.SupplierWebSocketHandler;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;

@Configuration
public class MinimaxWebSocketConfiguration {

    @Bean
    SupplierWebSocketHandler minimaxWebSocketHandler(
            MinimaxProviderAccess access, ProviderAccounts accountService) {
        return new SupplierWebSocketHandler(
                "minimax", access, accountService, MinimaxProtocol::normalizeWebSocketPath);
    }

    @Bean
    HandlerMapping minimaxWebSocketHandlerMapping(
            @Qualifier("minimaxWebSocketHandler") SupplierWebSocketHandler handler) {
        var mapping = new SimpleUrlHandlerMapping();
        mapping.setOrder(-1);
        mapping.setUrlMap(Map.of(
                ApiGroups.API_PREFIX + "/aiproviders/*/minimax/ws/**", handler));
        return mapping;
    }
}

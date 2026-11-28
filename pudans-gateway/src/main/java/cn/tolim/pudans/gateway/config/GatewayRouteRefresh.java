package cn.tolim.pudans.gateway.config;

import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Refreshes the gateway route table so newly registered services are routed without a restart
@Component
public class GatewayRouteRefresh {

    private final ApplicationEventPublisher publisher;

    public GatewayRouteRefresh(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${pudans.gateway.discovery.refresh-ms:15000}")
    public void refresh() {
        publisher.publishEvent(new RefreshRoutesEvent(this));
    }
}
package cn.tolim.pudans.security.contract.device;

import cn.tolim.pudans.security.contract.Device;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public interface DeviceSessions {

    Mono<Void> loginSuccess(ServerWebExchange exchange, Authentication successfullAuthentication);

    Mono<Void> changeSessionId(ServerWebExchange exchange);

    Mono<Void> revoke(String principalName, String deviceId);

    Mono<Void> revoke(String username);


    Mono<Device> resolveCurrentDevice(ServerWebExchange exchange);
}

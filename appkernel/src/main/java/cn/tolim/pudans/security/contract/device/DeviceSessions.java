package cn.tolim.pudans.security.contract.device;

import cn.tolim.pudans.security.contract.Device;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public interface DeviceSessions {

    // 登录成功后登记设备会话信息
    Mono<Void> loginSuccess(ServerWebExchange exchange, Authentication successfullAuthentication);

    // 认证后更换会话标识以防会话固定
    Mono<Void> changeSessionId(ServerWebExchange exchange);

    // 撤销指定主体的单台设备会话
    Mono<Void> revoke(String principalName, String deviceId);

    // 撤销指定主体的全部设备会话
    Mono<Void> revoke(String username);


    // 根据请求解析当前设备记录
    Mono<Device> resolveCurrentDevice(ServerWebExchange exchange);
}

package cn.tolim.pudans.security.authentication;

import org.springframework.security.config.web.server.ServerHttpSecurity;

public interface SecurityConfigurer {

    // 由各安全模块向安全配置注册自身规则。
    void configure(ServerHttpSecurity http);
}

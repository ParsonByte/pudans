package cn.tolim.pudans.security.authentication;

import org.springframework.security.config.web.server.ServerHttpSecurity;

public interface SecurityConfigurer {

    void configure(ServerHttpSecurity http);
}

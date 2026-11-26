package cn.tolim.pudans.security.web;

import cn.tolim.pudans.extension.contract.ApiGroups;
import org.springframework.web.server.ServerWebExchange;

public final class StaticRequestPaths {

    private static final String[] PREFIXES = {
        "/assets/", "/brand/", "/webjars/", ApiGroups.API_PREFIX + "/site-assets/"
    };

    private static final String[] FILES = {"/favicon.ico", "/login-bg.mp4"};

    private StaticRequestPaths() {}

    // 静态资源请求不参与审计与风险控制
    public static boolean isStatic(ServerWebExchange exchange) {
        var path = exchange.getRequest().getPath().value();
        for (var prefix : PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        for (var file : FILES) {
            if (path.equals(file)) {
                return true;
            }
        }
        return false;
    }
}

package cn.tolim.pudans.core.security.web;

import org.springframework.web.server.ServerWebExchange;

public final class StaticRequestPaths {

    private static final String[] PREFIXES = {"/assets/", "/brand/", "/webjars/"};

    private static final String[] FILES = {"/favicon.ico", "/login-bg.mp4"};

    private StaticRequestPaths() {}

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

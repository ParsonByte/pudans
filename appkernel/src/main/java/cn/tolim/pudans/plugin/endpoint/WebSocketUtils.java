package cn.tolim.pudans.plugin.endpoint;

import org.springframework.http.HttpHeaders;


public final class WebSocketUtils {

    private WebSocketUtils() {}

    public static boolean isWebSocketUpgrade(HttpHeaders headers) {
        var connection = headers.getFirst(HttpHeaders.CONNECTION);
        var upgrade = headers.getFirst(HttpHeaders.UPGRADE);
        return connection != null
                && connection.toLowerCase().contains("upgrade")
                && upgrade != null
                && upgrade.equalsIgnoreCase("websocket");
    }
}

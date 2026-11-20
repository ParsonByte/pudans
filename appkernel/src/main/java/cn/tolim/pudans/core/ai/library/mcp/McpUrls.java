package cn.tolim.pudans.core.ai.library.mcp;

import org.springframework.util.StringUtils;

final class McpUrls {

    private McpUrls() {}

    static String requireWebSocket(String url) {
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("WebSocket 传输必须配置 url。");
        }
        var value = url.strip();
        if (!value.startsWith("ws://") && !value.startsWith("wss://")) {
            throw new IllegalArgumentException("WebSocket url 必须以 ws:// 或 wss:// 开头。");
        }
        return value;
    }
}
package cn.tolim.pudans.core.ai.protocol;

public final class AiProtocolUrl {

    private AiProtocolUrl() {}

    public static String baseUrl(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public static String join(String baseUrl, String path) {
        var base = baseUrl(baseUrl);
        var suffix = path.startsWith("/") ? path : "/" + path;
        return base + suffix;
    }
}

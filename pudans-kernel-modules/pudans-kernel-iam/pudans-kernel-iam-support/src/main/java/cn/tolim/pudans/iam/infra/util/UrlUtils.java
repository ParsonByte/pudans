package cn.tolim.pudans.iam.infra.util;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;

public final class UrlUtils {

    private UrlUtils() {
    }

    public static String format(String url) {
        return url == null ? null : url.replaceAll("/{2,}", "/").replace(":/", "://");
    }

    public static boolean testUrlWithTimeOut(String url, int timeout) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(timeout)).GET().build();
            HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static String encodeUriComponent(String value) {
        return value == null ? null : URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20").replace("%21", "!").replace("%27", "'").replace("%28", "(")
                .replace("%29", ")").replace("%7E", "~");
    }

    public static String integrate(String url, Map<String, String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return url;
        }
        StringJoiner query = new StringJoiner("&");
        parameters.forEach((key, value) -> query.add(encodeUriComponent(key) + "="
                + encodeUriComponent(value)));
        return url + (url.contains("?") ? "&" : "?") + query;
    }
}

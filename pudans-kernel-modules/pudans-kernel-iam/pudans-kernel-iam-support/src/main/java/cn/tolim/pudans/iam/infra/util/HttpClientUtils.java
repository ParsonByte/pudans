package cn.tolim.pudans.iam.infra.util;

import org.apache.http.Header;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;

public final class HttpClientUtils {
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private HttpClientUtils() {
    }

    public static String post(String url, Map<String, String> parameters) throws IOException {
        return send(url, "POST", parameters, new Header[0]);
    }

    public static String get(String url, Map<String, String> parameters, Header... headers)
            throws IOException {
        return send(url, "GET", parameters, headers);
    }

    public static String get(String url) throws IOException {
        return get(url, Map.of());
    }

    public static String doGet(String url) {
        try {
            return get(url);
        } catch (IOException exception) {
            throw new IllegalStateException("HTTP GET 请求失败", exception);
        }
    }

    public static String doGet(String url, Map<String, String> parameters) {
        try {
            return get(url, parameters);
        } catch (IOException exception) {
            throw new IllegalStateException("HTTP GET 请求失败", exception);
        }
    }

    public static String doPost(String url) {
        try {
            return post(url, Map.of());
        } catch (IOException exception) {
            throw new IllegalStateException("HTTP POST 请求失败", exception);
        }
    }

    public static String doPost(String url, Map<String, String> parameters) {
        try {
            return post(url, parameters);
        } catch (IOException exception) {
            throw new IllegalStateException("HTTP POST 请求失败", exception);
        }
    }

    public static String doPostJson(String url, String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            return CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body();
        } catch (Exception exception) {
            throw new IllegalStateException("HTTP POST 请求失败", exception);
        }
    }

    private static String send(String url, String method, Map<String, String> parameters,
                               Header[] headers) throws IOException {
        try {
            String query = encode(parameters);
            HttpRequest.Builder builder;
            if ("GET".equals(method)) {
                String target = query.isEmpty() ? url : url + (url.contains("?") ? "&" : "?") + query;
                builder = HttpRequest.newBuilder(URI.create(target)).GET();
            } else {
                builder = HttpRequest.newBuilder(URI.create(url))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(query));
            }
            for (Header header : headers) {
                builder.header(header.getName(), header.getValue());
            }
            return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString()).body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP 请求被中断", exception);
        }
    }

    private static String encode(Map<String, String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "";
        }
        StringJoiner value = new StringJoiner("&");
        parameters.forEach((key, item) -> value.add(
                URLEncoder.encode(key, StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(item, StandardCharsets.UTF_8)));
        return value.toString();
    }
}

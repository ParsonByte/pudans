package cn.tolim.pudans.iam.infra.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.LinkedHashMap;
import java.util.Map;

public final class HttpRequestUtils {
    private HttpRequestUtils() {
    }

    public static MultiValueMap<String, String> getFormParameters(HttpServletRequest request) {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        request.getParameterMap().forEach((key, values) -> {
            for (String value : values) {
                parameters.add(key, value);
            }
        });
        return parameters;
    }

    public static MultiValueMap<String, String> getQueryParameters(HttpServletRequest request) {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        String query = request.getQueryString();
        if (query == null || query.isBlank()) {
            return parameters;
        }
        for (String pair : query.split("&")) {
            String[] values = pair.split("=", 2);
            if (values.length > 0) {
                parameters.add(values[0],
                        values.length > 1 ? java.net.URLDecoder.decode(values[1],
                                java.nio.charset.StandardCharsets.UTF_8) : "");
            }
        }
        return parameters;
    }

    public static Map<String, String> getRequestHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        request.getHeaderNames().asIterator()
                .forEachRemaining(name -> headers.put(name, request.getHeader(name)));
        return headers;
    }

    public static Map<String, String> getRequestParameters(HttpServletRequest request) {
        Map<String, String> parameters = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> parameters.put(key,
                String.join(",", values)));
        return parameters;
    }
}

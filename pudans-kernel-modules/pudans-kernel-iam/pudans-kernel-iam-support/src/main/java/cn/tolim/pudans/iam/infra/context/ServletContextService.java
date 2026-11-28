package cn.tolim.pudans.iam.infra.context;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ServletContextService {
    private static final List<MediaType> ALL_MEDIA_TYPES = Collections
            .singletonList(MediaType.ALL);

    private ServletContextService() {
    }

    public static HttpServletRequest getRequest() {
        return ((ServletRequestAttributes) Objects
                .requireNonNull(RequestContextHolder.getRequestAttributes())).getRequest();
    }

    public static HttpServletResponse getResponse() {
        return ((ServletRequestAttributes) Objects
                .requireNonNull(RequestContextHolder.getRequestAttributes())).getResponse();
    }

    public static HttpSession getSession() {
        return getRequest().getSession();
    }

    public static HttpSession getSession(boolean create) {
        return getRequest().getSession(create);
    }

    public static void setAttribute(String name, Object value) {
        getSession().setAttribute(name, value);
    }

    public static Object getAttribute(String name) {
        return getSession().getAttribute(name);
    }

    public static void removeAttribute(String name) {
        getSession().removeAttribute(name);
    }

    public static String getParameter(String name) {
        return getRequest().getParameter(name);
    }

    public static boolean isHtmlRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
    }

    public static List<MediaType> getAcceptedMediaTypes(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept == null || accept.isBlank() ? ALL_MEDIA_TYPES
                : MediaType.parseMediaTypes(accept);
    }

    public static JSONObject getRequestJsonObject(HttpServletRequest request) throws IOException {
        return JSON.parseObject(getRequestJsonString(request));
    }

    public static String getRequestJsonString(HttpServletRequest request) throws IOException {
        if (HttpMethod.GET.name().equals(request.getMethod())) {
            return request.getQueryString() == null ? ""
                    : URLDecoder.decode(request.getQueryString(), StandardCharsets.UTF_8);
        }
        return getRequestPostStr(request);
    }

    public static byte[] getRequestPostBytes(HttpServletRequest request) throws IOException {
        return request.getInputStream().readAllBytes();
    }

    public static String getRequestPostStr(HttpServletRequest request) throws IOException {
        return new String(getRequestPostBytes(request),
                Objects.toString(request.getCharacterEncoding(), StandardCharsets.UTF_8.name()));
    }

    public static Object getParameterObject(HttpServletRequest request, Object target) {
        var accessor = PropertyAccessorFactory.forBeanPropertyAccess(target);
        accessor.setAutoGrowNestedPaths(true);
        accessor.setPropertyValues(getParameterMap(request));
        return accessor.getWrappedInstance();
    }

    public static Map<String, String> getParameterMap(HttpServletRequest request) {
        Map<String, String> result = new HashMap<>();
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            String[] values = request.getParameterValues(name);
            if (values != null && values.length == 1 && !values[0].isEmpty()) {
                result.put(name, values[0]);
            }
        }
        return result;
    }

    public static Cookie readCookieByName(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(name)) {
                    return cookie;
                }
            }
        }
        return null;
    }

    public static HttpServletResponse setCookie(HttpServletResponse response, String name,
                                                String value, String path, int maxAge) {
        Cookie cookie = new Cookie(name, URLEncoder.encode(value, StandardCharsets.UTF_8));
        cookie.setPath(path);
        cookie.setMaxAge(maxAge);
        response.addCookie(cookie);
        return response;
    }

    public static Map<String, String> getParameterForUrl(String url) {
        Map<String, String> result = new HashMap<>();
        int index = url.indexOf('?');
        if (index < 0) {
            return result;
        }
        for (String pair : url.substring(index + 1).split("&")) {
            String[] values = pair.split("=", 2);
            if (values.length == 2) {
                result.put(URLDecoder.decode(values[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(values[1], StandardCharsets.UTF_8));
            }
        }
        return result;
    }

    public static String getServerUrl(HttpServletRequest request) {
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(request.getScheme()) && port == 80)
                || ("https".equals(request.getScheme()) && port == 443);
        return request.getScheme() + "://" + request.getServerName()
                + (defaultPort ? "" : ":" + port) + request.getContextPath();
    }

    public static String getServerRootPath(HttpServletRequest request) {
        return getServerUrl(request) + "/";
    }
}

package cn.tolim.pudans.infra.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.UnaryOperator;
import lombok.experimental.UtilityClass;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.reactive.function.server.ServerRequest;


@UtilityClass
public class PudansUtils {


    // 通过请求头判断是否为异步请求。
    public static boolean isXhr(HttpHeaders headers) {
        return headers.getOrEmpty("X-Requested-With").contains("XMLHttpRequest");
    }


    // 根据 Accept 判断客户端是否期望 HTML。
    public static boolean prefersHtml(HttpHeaders headers) {
        return headers.getAccept().stream()
                .anyMatch(mediaType -> mediaType.equalsTypeAndSubtype(MediaType.TEXT_HTML)
                        || mediaType.equalsTypeAndSubtype(MediaType.APPLICATION_XHTML_XML));
    }


    // 读取类路径文本，失败时给出资源位置。
    public static String readClassPathResourceAsString(String location) {
        ClassPathResource classPathResource = new ClassPathResource(location);
        try (InputStream inputStream = classPathResource.getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    String.format("无法从位置 [%s] 读取类路径文件内容", location), e);
        }
    }


    // 返回禁用缓存响应的请求处理器。
    public static UnaryOperator<ServerRequest> noCache() {
        return request -> {
            var headers = request.exchange().getResponse().getHeaders();
            headers.setCacheControl("no-store, no-cache, must-revalidate");
            headers.setPragma("no-cache");
            headers.setExpires(0);
            return request;
        };
    }
}

package cn.tolim.pudans.iam.infra.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class HttpResponseUtils {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule()).setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private HttpResponseUtils() {
    }

    public static void flushResponseJson(HttpServletResponse response, int status, Object body) {
        flushResponseJson(response, status, OBJECT_MAPPER, body);
    }

    public static void flushResponseJson(HttpServletResponse response, int status,
                                         ObjectMapper objectMapper, Object body) {
        try {
            response.setStatus(status);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(body == null ? "" : objectMapper.writeValueAsString(body));
        } catch (IOException exception) {
            throw new IllegalStateException("写入响应失败", exception);
        }
    }

    public static void flushResponse(HttpServletResponse response, String body) {
        try {
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write(body == null ? "" : body);
        } catch (IOException exception) {
            throw new IllegalStateException("写入响应失败", exception);
        }
    }

    public static <X> ResponseEntity<X> wrapOrNotFound(Optional<X> result) {
        return result.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    public static <X> ResponseEntity<X> wrapOrNotFound(Optional<X> result, HttpHeaders headers) {
        return result.map(value -> ResponseEntity.ok().headers(headers).body(value))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
}

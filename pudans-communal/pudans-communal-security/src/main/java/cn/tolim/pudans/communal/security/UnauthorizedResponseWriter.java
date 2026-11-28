package cn.tolim.pudans.communal.security;

import cn.tolim.pudans.communal.core.contract.ApiResponse;
import cn.tolim.pudans.communal.core.error.CommonErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class UnauthorizedResponseWriter {

    private UnauthorizedResponseWriter() {
    }

    static void write(HttpServletResponse response, ObjectMapper objectMapper) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(),
                ApiResponse.error(CommonErrorCode.UNAUTHORIZED.code(), CommonErrorCode.UNAUTHORIZED.message()));
    }
}

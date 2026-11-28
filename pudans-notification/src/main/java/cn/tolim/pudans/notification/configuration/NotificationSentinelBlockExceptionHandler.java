package cn.tolim.pudans.notification.configuration;

import cn.tolim.pudans.communal.core.contract.ApiResponse;
import cn.tolim.pudans.communal.core.error.CommonErrorCode;
import com.alibaba.csp.sentinel.adapter.spring.webmvc_v6x.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

@Component
public class NotificationSentinelBlockExceptionHandler implements BlockExceptionHandler {

    private final ObjectMapper objectMapper;

    public NotificationSentinelBlockExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, String resourceName,
                       BlockException exception) throws Exception {
        response.setStatus(CommonErrorCode.RATE_LIMITED.status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(
                CommonErrorCode.RATE_LIMITED.code(), CommonErrorCode.RATE_LIMITED.message()));
    }
}
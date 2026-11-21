package cn.tolim.pudans.infra.exception.handlers;

import static cn.tolim.pudans.infra.exception.Exceptions.createErrorResponse;

import java.util.Map;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webflux.error.DefaultErrorAttributes;
import org.springframework.web.reactive.function.server.ServerRequest;


public class ProblemDetailErrorAttributes extends DefaultErrorAttributes {

    // 用统一问题详情替换默认错误属性。
    @Override
    public Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options) {
        final var errAttributes = super.getErrorAttributes(request, options);
        var error = getError(request);
        var errorResponse = createErrorResponse(error, null, request.exchange());
        errAttributes.put("error", errorResponse.getBody());
        return errAttributes;
    }
}

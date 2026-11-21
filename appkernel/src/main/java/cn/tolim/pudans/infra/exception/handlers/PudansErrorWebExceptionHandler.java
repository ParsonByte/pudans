package cn.tolim.pudans.infra.exception.handlers;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.web.ErrorProperties;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.webflux.autoconfigure.error.DefaultErrorWebExceptionHandler;
import org.springframework.boot.webflux.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

public class PudansErrorWebExceptionHandler extends DefaultErrorWebExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(PudansErrorWebExceptionHandler.class);


    // 复用框架默认错误处理器的构造参数。
    public PudansErrorWebExceptionHandler(
            ErrorAttributes errorAttributes,
            WebProperties.Resources resources,
            ErrorProperties errorProperties,
            ApplicationContext applicationContext) {
        super(errorAttributes, resources, errorProperties, applicationContext);
    }

    // 从问题详情中读取状态码，保证响应与响应体一致。
    @Override
    protected int getHttpStatus(Map<String, Object> errorAttributes) {
        var problemDetail = (ProblemDetail) errorAttributes.get("error");
        return problemDetail.getStatus();
    }

    // 数据绑定异常属于客户端错误，仅在调试级别记录。
    @Override
    protected void logError(ServerRequest request, ServerResponse response, Throwable throwable) {
        if (throwable instanceof WebExchangeBindException) {
            if (logger.isDebugEnabled()) {
                logger.debug(
                        "已解析 [{}]，HTTP {} {}",
                        throwable.getClass().getSimpleName(),
                        request.method(),
                        request.path());
            }
            return;
        }
        super.logError(request, response, throwable);
    }

    // 以问题详情文本渲染错误，不再输出网页错误页。
    @Override
    protected Mono<ServerResponse> renderErrorResponse(ServerRequest request) {
        var errorAttributes = getErrorAttributes(request, getErrorAttributeOptions(request, MediaType.ALL));
        return ServerResponse.status(getHttpStatus(errorAttributes))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyValue(errorAttributes.get("error"));
    }

}

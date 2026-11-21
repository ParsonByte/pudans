package cn.tolim.pudans.core.infra.exception.handlers;

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


    public PudansErrorWebExceptionHandler(
            ErrorAttributes errorAttributes,
            WebProperties.Resources resources,
            ErrorProperties errorProperties,
            ApplicationContext applicationContext) {
        super(errorAttributes, resources, errorProperties, applicationContext);
    }

    @Override
    protected int getHttpStatus(Map<String, Object> errorAttributes) {
        var problemDetail = (ProblemDetail) errorAttributes.get("error");
        return problemDetail.getStatus();
    }

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

    @Override
    protected Mono<ServerResponse> renderErrorResponse(ServerRequest request) {
        var errorAttributes = getErrorAttributes(request, getErrorAttributeOptions(request, MediaType.ALL));
        return ServerResponse.status(getHttpStatus(errorAttributes))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyValue(errorAttributes.get("error"));
    }

}

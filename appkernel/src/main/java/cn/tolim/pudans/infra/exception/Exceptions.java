package cn.tolim.pudans.infra.exception;

import static org.springframework.core.annotation.MergedAnnotations.SearchStrategy.TYPE_HIERARCHY;

import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ServerWebExchange;

@Slf4j
public enum Exceptions {
    ;

    public static final String DEFAULT_TYPE = "about:blank";

    public static final String INVALID_CREDENTIAL_TYPE = "https://pudans.tolim.cn/probs/invalid-credential";

    public static final String REQUEST_NOT_PERMITTED_TYPE = "https://pudans.tolim.cn/probs/request-not-permitted";

    public static final String CONFLICT_TYPE = "https://pudans.tolim.cn/probs/conflict";

    public static final String SUDO_REQUIRED_TYPE = "https://pudans.tolim.cn/probs/sudo-required";


    public static final Map<Class<? extends Throwable>, String> EXCEPTION_TYPE_MAP = Map.of(
            RequestNotPermitted.class, REQUEST_NOT_PERMITTED_TYPE,
            BadCredentialsException.class, INVALID_CREDENTIAL_TYPE);

    public static ErrorResponse createErrorResponse(
            Throwable t, @Nullable HttpStatusCode status, ServerWebExchange exchange) {
        final ErrorResponse errorResponse;
        if (t instanceof ErrorResponse er) {
            errorResponse = er;
        } else {
            var er = handleConflictException(t);
            if (er == null) {
                er = handleException(t, status);
            }
            errorResponse = er;
        }
        var problemDetail = errorResponse.getBody();
        applyChineseText(errorResponse, problemDetail);


        if (t instanceof ApiException apiException) {
            problemDetail.setProperty("code", apiException.getCode());
        }
        problemDetail.setInstance(exchange.getRequest().getURI());
        problemDetail.setProperty("requestId", exchange.getRequest().getId());
        problemDetail.setProperty("timestamp", Instant.now());
        return errorResponse;
    }

    private static void applyChineseText(ErrorResponse errorResponse, ProblemDetail problemDetail) {
        var titleCode = errorResponse.getTitleMessageCode();
        var title = titleCode == null ? null : ProblemDetailMessages.title(titleCode);
        problemDetail.setTitle(title == null ? statusTitle(problemDetail.getStatus()) : title);

        var args = errorResponse.getDetailMessageArguments();
        var detailCode = errorResponse.getDetailMessageCode();
        var detail = detailCode == null ? null : ProblemDetailMessages.detail(detailCode, args);
        if (detail == null && problemDetail.getDetail() != null) {
            detail = ProblemDetailMessages.detail(problemDetail.getDetail(), args);
        }
        if (detail != null) {
            problemDetail.setDetail(detail);
        }
    }

    private static String statusTitle(int status) {
        return switch (status) {
            case 400 -> "请求参数有误";
            case 401 -> "认证失败";
            case 403 -> "访问被拒绝";
            case 404 -> "资源不存在";
            case 405 -> "请求方法不允许";
            case 406 -> "不可接受的响应类型";
            case 409 -> "冲突";
            case 415 -> "不支持的媒体类型";
            case 429 -> "请求过于频繁";
            default -> status >= 500 ? "服务器内部错误" : "请求失败";
        };
    }

    private static ErrorResponse handleException(Throwable t, @Nullable HttpStatusCode status) {
        var responseStatusAnno =
                MergedAnnotations.from(t.getClass(), TYPE_HIERARCHY).get(ResponseStatus.class);
        if (status == null) {
            status = responseStatusAnno.getValue("code", HttpStatus.class).orElse(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        var type = EXCEPTION_TYPE_MAP.getOrDefault(t.getClass(), DEFAULT_TYPE);
        var detail = switch (t) {
            case BadCredentialsException ignored -> "用户名或密码错误。";
            case LockedException ignored -> "账户已锁定，请联系管理员。";
            case DisabledException ignored -> "账户或访问令牌已停用或不可用。";
            case AccountExpiredException ignored -> "账户已过期，请联系管理员。";
            case CredentialsExpiredException ignored -> "登录凭据已过期，请重置密码。";
            default -> responseStatusAnno.getValue("reason", String.class).orElseGet(t::getMessage);
        };
        var builder = ErrorResponse.builder(t, status, detail).type(URI.create(type));
        if (status.is5xxServerError()) {
            builder.title("服务器内部错误").detail("服务器内部发生错误，请稍候再试。");
        }
        return builder.build();
    }

    private static @Nullable ErrorResponse handleConflictException(Throwable t) {
        if (t instanceof ConcurrencyFailureException) {
            return ErrorResponse.builder(t, ProblemDetail.forStatus(HttpStatus.CONFLICT))
                    .type(URI.create(CONFLICT_TYPE))
                    .title("冲突")
                    .detail("检测到冲突，请检查数据后重试。")
                    .build();
        }
        return null;
    }
}

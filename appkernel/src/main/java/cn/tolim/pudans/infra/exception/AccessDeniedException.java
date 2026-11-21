package cn.tolim.pudans.infra.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;


public class AccessDeniedException extends ResponseStatusException {

    // 使用默认拒绝原因创建访问拒绝异常。
    public AccessDeniedException() {
        this("访问被拒绝。");
    }

    // 使用调用方提供的拒绝原因创建异常。
    public AccessDeniedException(String reason) {
        this(reason, null, null);
    }

    // 附带问题详情码与参数，供统一错误响应使用。
    public AccessDeniedException(String reason, String detailCode, Object[] detailArgs) {
        super(HttpStatus.FORBIDDEN, reason, null, detailCode, detailArgs);
    }
}

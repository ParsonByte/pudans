package cn.tolim.pudans.infra.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public final class ProblemDetailFactory {

    private ProblemDetailFactory() {}

    // 构造带稳定错误码的标准问题详情。
    public static ProblemDetail create(HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}

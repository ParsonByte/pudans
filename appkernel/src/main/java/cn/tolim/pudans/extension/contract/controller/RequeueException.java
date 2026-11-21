package cn.tolim.pudans.extension.contract.controller;

import cn.tolim.pudans.extension.contract.controller.Reconciler.Result;


public class RequeueException extends RuntimeException {

    private final Result result;

    // 仅携带重试结果的异常。
    public RequeueException(Result result) {
        this(result, null);
    }

    // 携带重试结果与原因。
    public RequeueException(Result result, String reason) {
        this(result, reason, null);
    }

    // 携带重试结果、原因与根因。
    public RequeueException(Result result, String reason, Throwable t) {
        super(reason, t);
        this.result = result;
    }

    // 返回控制器据此重新入队的结果。
    public Result getResult() {
        return result;
    }
}

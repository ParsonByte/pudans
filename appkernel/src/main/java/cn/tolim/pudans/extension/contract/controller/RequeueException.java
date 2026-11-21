package cn.tolim.pudans.extension.contract.controller;

import cn.tolim.pudans.extension.contract.controller.Reconciler.Result;


public class RequeueException extends RuntimeException {

    private final Result result;

    public RequeueException(Result result) {
        this(result, null);
    }

    public RequeueException(Result result, String reason) {
        this(result, reason, null);
    }

    public RequeueException(Result result, String reason, Throwable t) {
        super(reason, t);
        this.result = result;
    }

    public Result getResult() {
        return result;
    }
}

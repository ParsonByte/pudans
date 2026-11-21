package cn.tolim.pudans.extension.contract.controller;

import reactor.core.Disposable;

public interface Synchronizer<R> extends Disposable {

    // 启动同步流程，实现需保证幂等。
    void start();
}

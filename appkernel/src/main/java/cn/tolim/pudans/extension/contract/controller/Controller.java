package cn.tolim.pudans.extension.contract.controller;

import reactor.core.Disposable;

public interface Controller extends Disposable {

    // 返回控制器名称，用于日志区分实例。
    String getName();

    // 启动同步器与工作线程。
    void start();
}

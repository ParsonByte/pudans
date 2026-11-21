package cn.tolim.pudans.extension.contract.controller;

import reactor.core.Disposable;

public interface Synchronizer<R> extends Disposable {

    void start();
}

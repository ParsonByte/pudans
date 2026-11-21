package cn.tolim.pudans.extension.api.controller;

import reactor.core.Disposable;

public interface Synchronizer<R> extends Disposable {

    void start();
}

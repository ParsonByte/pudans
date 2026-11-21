package cn.tolim.pudans.extension.api.controller;

import reactor.core.Disposable;

public interface Controller extends Disposable {

    String getName();

    void start();
}

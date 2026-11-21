package cn.tolim.pudans.extension.contract.controller;

import reactor.core.Disposable;

public interface Controller extends Disposable {

    String getName();

    void start();
}

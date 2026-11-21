package cn.tolim.pudans.extension.controller;

import static org.springframework.core.ResolvableType.forClassWithGenerics;

import cn.tolim.pudans.extension.contract.ExtensionClient;
import cn.tolim.pudans.extension.contract.controller.Controller;
import cn.tolim.pudans.extension.contract.controller.ControllerBuilder;
import cn.tolim.pudans.extension.contract.controller.Reconciler;
import cn.tolim.pudans.extension.contract.controller.Reconciler.Request;
import cn.tolim.pudans.infra.setting.InitializationPhase;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.SmartLifecycle;

@Slf4j
public class DefaultControllerManager implements ApplicationContextAware, SmartLifecycle {

    private final ExtensionClient client;
    private final ConcurrentHashMap<String, Controller> controllers;
    private ApplicationContext applicationContext;
    private volatile boolean running;

    // 保存扩展客户端并初始化控制器注册表。
    public DefaultControllerManager(ExtensionClient client) {
        this.client = client;
        controllers = new ConcurrentHashMap<>();
    }

    // 关闭控制器并隔离异常以免中断整体停止。
    private static void disposeSilently(Controller controller) {
        if (controller == null) {
            return;
        }
        try {
            log.info("正在关闭控制器 {}...", controller.getName());
            controller.dispose();
            log.info("控制器 {} 已成功关闭", controller.getName());
        } catch (Throwable t) {
            log.error("销毁控制器 {} 失败", controller.getName(), t);
        }
    }

    // 启动全部协调器对应的控制器。
    @Override
    public void start() {
        if (this.running) {
            return;
        }
        this.running = true;

        applicationContext
                .<Reconciler<Request>>getBeanProvider(forClassWithGenerics(Reconciler.class, Request.class))
                .orderedStream()
                .forEach(this::start);
    }

    // 构建并启动单个协调器的控制器。
    void start(Reconciler<Request> reconciler) {
        var builder = new ControllerBuilder(reconciler, client);
        var controller = reconciler.setupWith(builder);
        controllers.put(reconciler.getClass().getName(), controller);
        controller.start();
    }

    // 停止并释放已注册的全部控制器。
    @Override
    public void stop() {
        if (!running) {
            return;
        }
        this.running = false;
        log.info("正在关闭 {} 个控制器...", controllers.size());
        controllers.forEach((name, controller) -> disposeSilently(controller));
        log.info("已关闭 {} 个控制器。", controllers.size());
    }

    // 保存应用上下文用于查找协调器实例。
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    // 返回生命周期是否处于运行状态。
    @Override
    public boolean isRunning() {
        return running;
    }

    // 在控制器初始化阶段启动。
    @Override
    public int getPhase() {
        return InitializationPhase.CONTROLLERS.getPhase();
    }
}

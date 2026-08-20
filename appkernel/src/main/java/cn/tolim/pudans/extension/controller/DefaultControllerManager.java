package cn.tolim.pudans.extension.controller;

import static org.springframework.core.ResolvableType.forClassWithGenerics;

import cn.tolim.pudans.contract.extension.ExtensionClient;
import cn.tolim.pudans.contract.extension.controller.Controller;
import cn.tolim.pudans.contract.extension.controller.ControllerBuilder;
import cn.tolim.pudans.contract.extension.controller.Reconciler;
import cn.tolim.pudans.contract.extension.controller.Reconciler.Request;
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

    public DefaultControllerManager(ExtensionClient client) {
        this.client = client;
        controllers = new ConcurrentHashMap<>();
    }

    private static void disposeSilently(Controller controller) {
        if (controller == null) {
            return;
        }
        try {
            log.info("Shutting down controller {}...", controller.getName());
            controller.dispose();
            log.info("Shutdown controller {} successfully", controller.getName());
        } catch (Throwable t) {
            log.error("Failed to dispose controller {}", controller.getName(), t);
        }
    }

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

    void start(Reconciler<Request> reconciler) {
        var builder = new ControllerBuilder(reconciler, client);
        var controller = reconciler.setupWith(builder);
        controllers.put(reconciler.getClass().getName(), controller);
        controller.start();
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        this.running = false;
        log.info("Shutting down {} controllers...", controllers.size());
        controllers.forEach((name, controller) -> disposeSilently(controller));
        log.info("Shutdown {} controllers.", controllers.size());
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return InitializationPhase.CONTROLLERS.getPhase();
    }
}

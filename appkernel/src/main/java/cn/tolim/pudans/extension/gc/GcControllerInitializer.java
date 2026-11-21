package cn.tolim.pudans.extension.gc;

import cn.tolim.pudans.extension.api.controller.Controller;
import cn.tolim.pudans.infra.setting.InitializationPhase;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
class GcControllerInitializer implements SmartLifecycle {

    private final Controller gcController;
    private volatile boolean running;

    public GcControllerInitializer(GcReconciler gcReconciler) {
        this.gcController = gcReconciler.setupWith(null);
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        gcController.start();
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        gcController.dispose();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return InitializationPhase.GC_CONTROLLER.getPhase();
    }
}

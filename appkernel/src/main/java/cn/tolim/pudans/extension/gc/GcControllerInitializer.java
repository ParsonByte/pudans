package cn.tolim.pudans.extension.gc;

import cn.tolim.pudans.extension.contract.controller.Controller;
import cn.tolim.pudans.infra.setting.InitializationPhase;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
class GcControllerInitializer implements SmartLifecycle {

    private final Controller gcController;
    private volatile boolean running;

    // 由协调器创建垃圾回收控制器。
    public GcControllerInitializer(GcReconciler gcReconciler) {
        this.gcController = gcReconciler.setupWith(null);
    }

    // 启动垃圾回收控制器并防止重复调用。
    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        gcController.start();
    }

    // 停止并释放垃圾回收控制器。
    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        gcController.dispose();
    }

    // 返回控制器生命周期运行状态。
    @Override
    public boolean isRunning() {
        return running;
    }

    // 在垃圾回收阶段启动控制器。
    @Override
    public int getPhase() {
        return InitializationPhase.GC_CONTROLLER.getPhase();
    }
}

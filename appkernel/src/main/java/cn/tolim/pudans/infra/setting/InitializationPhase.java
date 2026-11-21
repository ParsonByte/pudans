package cn.tolim.pudans.infra.setting;


public enum InitializationPhase {
    SCHEME(Integer.MIN_VALUE + 100),

    EXTENSION_RESOURCES,


    GC_CONTROLLER,

    CONTROLLERS,
    ;

    private static final int GAP = 100;

    private final int phase;

    // 未显式指定时按枚举顺序留出固定间隔。
    InitializationPhase() {
        this.phase = ordinal() * GAP;
    }

    // 显式阶段值用于固定关键组件的启动顺序。
    InitializationPhase(int phase) {
        this.phase = phase;
    }

    // 返回该阶段在生命周期中的顺序值。
    public int getPhase() {
        return phase;
    }
}

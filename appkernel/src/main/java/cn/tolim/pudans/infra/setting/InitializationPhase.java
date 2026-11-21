package cn.tolim.pudans.infra.setting;


public enum InitializationPhase {
    FIRST(Integer.MIN_VALUE),

    SCHEME(Integer.MIN_VALUE + 100),

    EXTENSION_RESOURCES,


    GC_CONTROLLER,

    CONTROLLERS,

    LAST(Integer.MAX_VALUE),
    ;

    private static final int GAP = 100;

    private final int phase;

    InitializationPhase() {
        this.phase = ordinal() * GAP;
    }

    InitializationPhase(int phase) {
        this.phase = phase;
    }

    public int getPhase() {
        return phase;
    }
}

package cn.tolim.pudans.core.ai.plan;

public enum PlanVoucherKind {
    FIVE_HOUR_RESET(PlanWindowKind.FIVE_HOUR),
    WEEKLY_RESET(PlanWindowKind.WEEKLY);

    private final PlanWindowKind target;

    PlanVoucherKind(PlanWindowKind target) {
        this.target = target;
    }

    public PlanWindowKind target() {
        return target;
    }
}
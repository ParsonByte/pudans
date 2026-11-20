package cn.tolim.pudans.core.ai.harness.plan;

public final class PlanModeState {

    private boolean active;

    private String planContent = "";

    public boolean active() {
        return active;
    }

    public String planContent() {
        return planContent;
    }

    public void enter() {
        active = true;
    }

    public void write(String content) {
        planContent = content == null ? "" : content;
        active = true;
    }

    public void exit() {
        active = false;
    }

    public void restore(boolean active, String content) {
        this.active = active;
        this.planContent = content == null ? "" : content;
    }

    public PlanModeState copy() {
        var copy = new PlanModeState();
        copy.active = active;
        copy.planContent = planContent;
        return copy;
    }
}
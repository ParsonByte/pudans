package cn.tolim.pudans.contract.live;

public enum LiveRecordingState {
    QUEUED,
    RECORDING,
    FINALIZING,
    COMPLETED,
    FAILED,
    CANCELED;

    // 排队、录制中与收尾中都属于活动状态。
    public boolean isActive() {
        return this == QUEUED || this == RECORDING || this == FINALIZING;
    }
}

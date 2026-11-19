package cn.tolim.pudans.core.ai.memory;

public record CaptureResult(int l0RecordedCount, boolean pipelineScheduled, boolean skippedBySessionFilter) {

    public CaptureResult {
        if (l0RecordedCount < 0) {
            throw new IllegalArgumentException("l0RecordedCount 不能为负数");
        }
    }
}

package cn.tolim.pudans.contract.job.enums;

// Behavior when a job execution overlaps with a previous execution
public enum BlockStrategy {
    PARALLEL,
    SERIAL,
    DISCARD
}
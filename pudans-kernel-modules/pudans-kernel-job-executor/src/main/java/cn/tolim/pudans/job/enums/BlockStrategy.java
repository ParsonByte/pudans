package cn.tolim.pudans.job.enums;

// Behavior when a job execution overlaps with a previous execution
public enum BlockStrategy {

    // Run concurrently, no per-job lock
    PARALLEL,

    // Wait for the previous execution to finish, up to the execution timeout
    SERIAL,

    // Drop the new execution when the previous one is still running
    DISCARD
}
package cn.tolim.pudans.core.ai.runtime.sandbox;

// 运行时状态机与持久化状态保持一一对应。
public enum SandboxState {
    CREATING,
    RUNNING,
    STOPPING,
    STOPPED,
    FAILED,
    DESTROYED
}
package cn.tolim.pudans.core.ai.protocol.agentprotocol;

import java.util.Locale;

public enum AgentProtocolRunStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
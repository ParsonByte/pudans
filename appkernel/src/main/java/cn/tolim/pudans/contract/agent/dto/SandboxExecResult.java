package cn.tolim.pudans.contract.agent.dto;

import cn.tolim.pudans.contract.agent.SandboxOperation;
import java.time.Instant;
import java.util.UUID;

public record SandboxExecResult(
        UUID operationId,
        SandboxOperation.Status status,
        Integer exitCode,
        String stdout,
        String stderr,
        boolean outputTruncated,
        Instant startedAt,
        Instant finishedAt) {}

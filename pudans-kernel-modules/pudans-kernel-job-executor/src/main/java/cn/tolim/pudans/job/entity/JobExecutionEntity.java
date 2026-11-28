package cn.tolim.pudans.job.entity;

import cn.tolim.pudans.job.enums.ExecutionStatus;
import cn.tolim.pudans.job.enums.TriggerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

// One attempt of running a job, kept for history and diagnostics
@Getter
@Setter
@Accessors(chain = true)
@Entity
@Table(name = "pudans_job_execution", indexes = {
        @Index(name = "idx_job_execution_job_id", columnList = "job_id"),
        @Index(name = "idx_job_execution_status", columnList = "status_"),
        @Index(name = "idx_job_execution_create_time", columnList = "create_time")
})
public class JobExecutionEntity extends AuditableEntity {

    // Job definition identifier
    @Column(name = "job_id", nullable = false, length = 64)
    private String jobId;

    // Snapshot of the job code at execution time
    @Column(name = "job_code", nullable = false, length = 100)
    private String jobCode;

    // Snapshot of the job name at execution time
    @Column(name = "job_name", length = 200)
    private String jobName;

    // Snapshot of the handler name at execution time
    @Column(name = "handler_", nullable = false, length = 200)
    private String handlerName;

    // What triggered this execution
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 32)
    private TriggerType triggerType;

    // Execution lifecycle status
    @Enumerated(EnumType.STRING)
    @Column(name = "status_", nullable = false, length = 32)
    private ExecutionStatus status;

    // Attempt number, starting at one
    @Column(name = "attempt_", nullable = false)
    private int attempt;

    // Total attempts allowed including the first one
    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    // When the execution was accepted for running
    @Column(name = "scheduled_time")
    private LocalDateTime scheduledTime;

    // When the handler actually started
    @Column(name = "start_time")
    private LocalDateTime startTime;

    // When the execution reached a terminal status
    @Column(name = "end_time")
    private LocalDateTime endTime;

    // Wall clock duration in milliseconds
    @Column(name = "duration_ms")
    private Long durationMs;

    // Executor instance that ran the job
    @Column(name = "instance_name", length = 200)
    private String instanceName;

    // Snapshot of the handler parameters
    @Column(name = "params_", columnDefinition = "TEXT")
    private String params;

    // Handler result message on success
    @Column(name = "result_", columnDefinition = "TEXT")
    private String result;

    // Failure or skip reason
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // Identifier of the failed execution this retry belongs to
    @Column(name = "retry_of_execution_id", length = 64)
    private String retryOfExecutionId;

    // Operator or component that requested the execution
    @Column(name = "triggered_by", length = 100)
    private String triggeredBy;
}
package cn.tolim.pudans.job.entity;

import cn.tolim.pudans.job.enums.BlockStrategy;
import cn.tolim.pudans.job.enums.JobStatus;
import cn.tolim.pudans.job.enums.ScheduleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.annotations.SoftDelete;

// A registered job: what to run, how to schedule it and how to handle overlap
@Getter
@Setter
@Accessors(chain = true)
@Entity
@Table(name = "pudans_job_definition")
@SoftDelete(columnName = "is_deleted")
public class JobDefinitionEntity extends AuditableEntity {

    // Stable business code, unique among non-deleted definitions
    @Column(name = "code_", nullable = false, length = 100)
    private String code;

    // Display name
    @Column(name = "name_", nullable = false, length = 200)
    private String name;

    // Name of the registered JobHandler bean
    @Column(name = "handler_", nullable = false, length = 200)
    private String handlerName;

    // Scheduling strategy
    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false, length = 32)
    private ScheduleType scheduleType;

    // Spring cron expression used when scheduleType is CRON
    @Column(name = "cron_expression", length = 200)
    private String cronExpression;

    // Period in milliseconds used when scheduleType is FIXED_RATE or FIXED_DELAY
    @Column(name = "period_millis")
    private Long periodMillis;

    // Delay in milliseconds before the first fixed-rate or fixed-delay execution
    @Column(name = "initial_delay_millis")
    private Long initialDelayMillis;

    // Handler specific parameters, usually a JSON document
    @Column(name = "params_", columnDefinition = "TEXT")
    private String params;

    // Per-execution timeout in seconds, zero or negative disables the timeout
    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds;

    // Number of automatic retries after a failed execution
    @Column(name = "max_retries")
    private Integer maxRetries;

    // Delay in seconds before an automatic retry
    @Column(name = "retry_interval_seconds")
    private Integer retryIntervalSeconds;

    // Overlap handling strategy
    @Enumerated(EnumType.STRING)
    @Column(name = "block_strategy", nullable = false, length = 32)
    private BlockStrategy blockStrategy;

    // Whether the scheduler should pick the job up
    @Enumerated(EnumType.STRING)
    @Column(name = "status_", nullable = false, length = 32)
    private JobStatus status;

    // Human readable description
    @Column(name = "description_", length = 500)
    private String description;
}
package cn.tolim.pudans.job.service;

import cn.tolim.pudans.job.configuration.JobExecutorProperties;
import cn.tolim.pudans.job.dto.JobDefinitionRequest;
import cn.tolim.pudans.job.dto.JobDefinitionResponse;
import cn.tolim.pudans.job.dto.PageResponse;
import cn.tolim.pudans.job.entity.JobDefinitionEntity;
import cn.tolim.pudans.job.enums.BlockStrategy;
import cn.tolim.pudans.job.enums.JobStatus;
import cn.tolim.pudans.job.enums.ScheduleType;
import cn.tolim.pudans.job.handler.JobHandlerRegistry;
import cn.tolim.pudans.job.repository.JobDefinitionRepository;
import cn.tolim.pudans.job.scheduling.JobScheduleManager;
import cn.tolim.pudans.job.support.JobErrors;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

// CRUD and lifecycle management of job definitions
@Service
public class JobDefinitionService {

    private static final int MAX_PAGE_SIZE = 200;

    private final JobDefinitionRepository jobDefinitionRepository;
    private final JobHandlerRegistry jobHandlerRegistry;
    private final JobScheduleManager jobScheduleManager;
    private final JobExecutorProperties properties;

    public JobDefinitionService(JobDefinitionRepository jobDefinitionRepository,
                                JobHandlerRegistry jobHandlerRegistry,
                                JobScheduleManager jobScheduleManager,
                                JobExecutorProperties properties) {
        this.jobDefinitionRepository = jobDefinitionRepository;
        this.jobHandlerRegistry = jobHandlerRegistry;
        this.jobScheduleManager = jobScheduleManager;
        this.properties = properties;
    }

    // Register a new job definition
    @Transactional
    public JobDefinitionResponse create(JobDefinitionRequest request, String actor) {
        String code = normalizeCode(request.code());
        if (jobDefinitionRepository.existsByCode(code)) {
            throw JobErrors.jobCodeExists(code);
        }
        validateHandler(request.handlerName());
        validateSchedule(request.scheduleType(), request.cronExpression(),
                request.periodMillis());
        JobDefinitionEntity entity = new JobDefinitionEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setCreateBy(actor(actor));
        entity.setUpdateBy(actor(actor));
        apply(entity, request);
        JobDefinitionEntity saved = jobDefinitionRepository.save(entity);
        syncSchedule(saved);
        return toResponse(saved);
    }

    // Replace an existing job definition
    @Transactional
    public JobDefinitionResponse update(String id, JobDefinitionRequest request, String actor) {
        JobDefinitionEntity entity = requireEntity(id);
        String code = normalizeCode(request.code());
        if (!code.equals(entity.getCode()) && jobDefinitionRepository.existsByCode(code)) {
            throw JobErrors.jobCodeExists(code);
        }
        validateHandler(request.handlerName());
        validateSchedule(request.scheduleType(), request.cronExpression(),
                request.periodMillis());
        apply(entity, request);
        entity.setUpdateBy(actor(actor));
        JobDefinitionEntity saved = jobDefinitionRepository.save(entity);
        syncSchedule(saved);
        return toResponse(saved);
    }

    // Soft delete a job definition and stop scheduling it
    @Transactional
    public void delete(String id) {
        JobDefinitionEntity entity = requireEntity(id);
        jobScheduleManager.unschedule(entity.getId());
        jobDefinitionRepository.delete(entity);
    }

    // Enable or disable a job
    @Transactional
    public JobDefinitionResponse setEnabled(String id, boolean enabled, String actor) {
        JobDefinitionEntity entity = requireEntity(id);
        entity.setStatus(enabled ? JobStatus.ENABLED : JobStatus.DISABLED);
        entity.setUpdateBy(actor(actor));
        JobDefinitionEntity saved = jobDefinitionRepository.save(entity);
        syncSchedule(saved);
        return toResponse(saved);
    }

    // One job definition
    @Transactional(readOnly = true)
    public JobDefinitionResponse get(String id) {
        return toResponse(requireEntity(id));
    }

    // Paged job definitions with optional filters
    @Transactional(readOnly = true)
    public PageResponse<JobDefinitionResponse> list(JobStatus status, String keyword, int page,
                                                     int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page),
                Math.min(Math.max(1, size), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createTime"));
        return PageResponse.from(jobDefinitionRepository.findAll(toSpecification(status, keyword),
                pageable), this::toResponse);
    }

    // Load a definition or fail with 404
    @Transactional(readOnly = true)
    public JobDefinitionEntity requireEntity(String id) {
        return jobDefinitionRepository.findById(id)
                .orElseThrow(() -> JobErrors.jobNotFound(id));
    }

    private void apply(JobDefinitionEntity entity, JobDefinitionRequest request) {
        entity.setCode(normalizeCode(request.code()));
        entity.setName(required(request.name(), "name"));
        entity.setHandlerName(required(request.handlerName(), "handlerName"));
        entity.setScheduleType(request.scheduleType());
        entity.setCronExpression(blankToNull(request.cronExpression()));
        entity.setPeriodMillis(request.periodMillis());
        entity.setInitialDelayMillis(request.initialDelayMillis() == null
                ? 0L : Math.max(0L, request.initialDelayMillis()));
        entity.setParams(blankToNull(request.params()));
        entity.setTimeoutSeconds(request.timeoutSeconds() == null
                ? properties.getDefaultTimeoutSeconds() : request.timeoutSeconds());
        entity.setMaxRetries(request.maxRetries() == null ? 0 : request.maxRetries());
        entity.setRetryIntervalSeconds(request.retryIntervalSeconds() == null
                ? 0 : request.retryIntervalSeconds());
        entity.setBlockStrategy(request.blockStrategy() == null
                ? BlockStrategy.SERIAL : request.blockStrategy());
        entity.setStatus(Boolean.TRUE.equals(request.enabled())
                ? JobStatus.ENABLED : JobStatus.DISABLED);
        entity.setDescription(blankToNull(request.description()));
        entity.setRemark(blankToNull(request.remark()));
        // Remove fields that do not apply to the selected schedule type
        if (request.scheduleType() == ScheduleType.CRON) {
            entity.setPeriodMillis(null);
        } else if (request.scheduleType() == ScheduleType.FIXED_RATE
                || request.scheduleType() == ScheduleType.FIXED_DELAY) {
            entity.setCronExpression(null);
        } else if (request.scheduleType() == ScheduleType.MANUAL) {
            entity.setCronExpression(null);
            entity.setPeriodMillis(null);
        }
    }

    private void validateHandler(String handlerName) {
        String name = required(handlerName, "handlerName");
        if (!jobHandlerRegistry.contains(name)) {
            throw JobErrors.handlerNotFound(name);
        }
    }

    private void validateSchedule(ScheduleType scheduleType, String cronExpression,
                                  Long periodMillis) {
        if (scheduleType == null) {
            throw JobErrors.invalidRequest("scheduleType is required");
        }
        switch (scheduleType) {
            case CRON -> {
                if (cronExpression == null || cronExpression.isBlank()) {
                    throw JobErrors.invalidSchedule(
                            "cronExpression is required for CRON schedules");
                }
                try {
                    CronExpression.parse(cronExpression.trim());
                } catch (IllegalArgumentException ex) {
                    throw JobErrors.invalidSchedule("Invalid cron expression '"
                            + cronExpression + "': " + ex.getMessage());
                }
            }
            case FIXED_RATE, FIXED_DELAY -> {
                if (periodMillis == null || periodMillis <= 0) {
                    throw JobErrors.invalidSchedule(
                            "periodMillis must be positive for " + scheduleType + " schedules");
                }
            }
            case MANUAL -> {
                // No schedule parameters are required
            }
        }
    }

    private void syncSchedule(JobDefinitionEntity entity) {
        if (entity.getStatus() == JobStatus.ENABLED) {
            jobScheduleManager.scheduleAfterCommit(entity);
        } else {
            jobScheduleManager.unschedule(entity.getId());
        }
    }

    private JobDefinitionResponse toResponse(JobDefinitionEntity entity) {
        return JobDefinitionResponse.from(entity, jobScheduleManager.isScheduled(entity.getId()),
                jobScheduleManager.nextFireTime(entity).orElse(null));
    }

    private Specification<JobDefinitionEntity> toSpecification(JobStatus status,
                                                               String keyword) {
        return (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("code")), pattern),
                        builder.like(builder.lower(root.get("name")), pattern)));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String normalizeCode(String code) {
        String value = required(code, "code").trim();
        if (!value.matches("[A-Za-z0-9_.-]+")) {
            throw JobErrors.invalidRequest(
                    "code may only contain letters, digits, dot, underscore and dash");
        }
        return value;
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw JobErrors.invalidRequest(field + " is required");
        }
        return value.trim();
    }

    private String actor(String actor) {
        return actor == null || actor.isBlank() ? "system" : actor;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
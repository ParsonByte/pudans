package cn.tolim.pudans.job.service;

import cn.tolim.pudans.job.dto.JobExecutionQuery;
import cn.tolim.pudans.job.dto.JobExecutionResponse;
import cn.tolim.pudans.job.dto.JobExecutionStatsResponse;
import cn.tolim.pudans.job.dto.PageResponse;
import cn.tolim.pudans.job.entity.JobExecutionEntity;
import cn.tolim.pudans.job.enums.ExecutionStatus;
import cn.tolim.pudans.job.repository.JobDefinitionRepository;
import cn.tolim.pudans.job.repository.JobExecutionRepository;
import cn.tolim.pudans.job.support.JobErrors;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Read only queries over execution history
@Service
public class JobExecutionQueryService {

    private static final int MAX_PAGE_SIZE = 200;

    private final JobExecutionRepository jobExecutionRepository;
    private final JobDefinitionRepository jobDefinitionRepository;

    public JobExecutionQueryService(JobExecutionRepository jobExecutionRepository,
                                    JobDefinitionRepository jobDefinitionRepository) {
        this.jobExecutionRepository = jobExecutionRepository;
        this.jobDefinitionRepository = jobDefinitionRepository;
    }

    // Paged execution history with optional filters
    @Transactional(readOnly = true)
    public PageResponse<JobExecutionResponse> list(JobExecutionQuery query) {
        Pageable pageable = PageRequest.of(Math.max(0, query.page()),
                Math.min(Math.max(1, query.size()), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createTime"));
        return PageResponse.from(jobExecutionRepository.findAll(toSpecification(query),
                pageable), JobExecutionResponse::from);
    }

    // One execution record
    @Transactional(readOnly = true)
    public JobExecutionResponse get(String id) {
        return JobExecutionResponse.from(requireExecution(id));
    }

    // Aggregated counters and the last execution for one job definition
    @Transactional(readOnly = true)
    public JobExecutionStatsResponse stats(String jobId) {
        if (!jobDefinitionRepository.existsById(jobId)) {
            throw JobErrors.jobNotFound(jobId);
        }
        Optional<JobExecutionEntity> last = jobExecutionRepository
                .findFirstByJobIdOrderByCreateTimeDesc(jobId);
        JobExecutionStatsResponse.LastExecution lastExecution = last
                .map(entity -> new JobExecutionStatsResponse.LastExecution(entity.getId(),
                        entity.getStatus(), entity.getTriggerType(), entity.getStartTime(),
                        entity.getEndTime(), entity.getDurationMs()))
                .orElse(null);
        return new JobExecutionStatsResponse(jobId,
                count(jobId, null),
                count(jobId, ExecutionStatus.QUEUED),
                count(jobId, ExecutionStatus.RUNNING),
                count(jobId, ExecutionStatus.SUCCESS),
                count(jobId, ExecutionStatus.FAILED),
                count(jobId, ExecutionStatus.TIMEOUT),
                count(jobId, ExecutionStatus.SKIPPED),
                count(jobId, ExecutionStatus.CANCELED),
                lastExecution);
    }

    private long count(String jobId, ExecutionStatus status) {
        return status == null ? jobExecutionRepository.countByJobId(jobId)
                : jobExecutionRepository.countByJobIdAndStatus(jobId, status);
    }

    private JobExecutionEntity requireExecution(String id) {
        return jobExecutionRepository.findById(id)
                .orElseThrow(() -> JobErrors.executionNotFound(id));
    }

    private Specification<JobExecutionEntity> toSpecification(JobExecutionQuery query) {
        return (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.jobId() != null && !query.jobId().isBlank()) {
                predicates.add(builder.equal(root.get("jobId"), query.jobId()));
            }
            if (query.jobCode() != null && !query.jobCode().isBlank()) {
                predicates.add(builder.equal(root.get("jobCode"), query.jobCode()));
            }
            if (query.status() != null) {
                predicates.add(builder.equal(root.get("status"), query.status()));
            }
            if (query.triggerType() != null) {
                predicates.add(builder.equal(root.get("triggerType"), query.triggerType()));
            }
            if (query.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createTime"),
                        query.from()));
            }
            if (query.to() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("createTime"), query.to()));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
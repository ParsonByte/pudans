package cn.tolim.pudans.job.repository;

import cn.tolim.pudans.job.entity.JobExecutionEntity;
import cn.tolim.pudans.job.enums.ExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobExecutionRepository extends JpaRepository<JobExecutionEntity, String>,
        JpaSpecificationExecutor<JobExecutionEntity> {

    // Counts executions of a job in the given status
    long countByJobIdAndStatus(String jobId, ExecutionStatus status);

    // Counts all executions of a job
    long countByJobId(String jobId);

    // Counts executions in the given status across all jobs
    long countByStatus(ExecutionStatus status);

    // Returns the most recent execution of a job
    Optional<JobExecutionEntity> findFirstByJobIdOrderByCreateTimeDesc(String jobId);

    // Returns executions in one of the given statuses
    List<JobExecutionEntity> findByStatusIn(Collection<ExecutionStatus> statuses);

    // Returns executions in the given statuses that belong to the given executor instance
    List<JobExecutionEntity> findByStatusInAndInstanceName(Collection<ExecutionStatus> statuses,
                                                           String instanceName);

    // Returns executions still marked as running before the given moment
    List<JobExecutionEntity> findByStatusAndStartTimeBefore(ExecutionStatus status,
                                                            LocalDateTime threshold);

    // Returns executions that finished before the given moment in the given statuses
    List<JobExecutionEntity> findByStatusInAndEndTimeBefore(Collection<ExecutionStatus> statuses,
                                                            LocalDateTime threshold);
}
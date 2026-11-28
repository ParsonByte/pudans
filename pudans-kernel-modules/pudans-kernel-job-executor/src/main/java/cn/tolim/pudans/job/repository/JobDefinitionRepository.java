package cn.tolim.pudans.job.repository;

import cn.tolim.pudans.job.entity.JobDefinitionEntity;
import cn.tolim.pudans.job.enums.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobDefinitionRepository extends JpaRepository<JobDefinitionEntity, String>,
        JpaSpecificationExecutor<JobDefinitionEntity> {

    // Returns the definition with the given business code, if present
    Optional<JobDefinitionEntity> findByCode(String code);

    // Checks whether a non-deleted definition with the given code exists
    boolean existsByCode(String code);

    // Returns all definitions with the given status
    List<JobDefinitionEntity> findAllByStatus(JobStatus status);

    // Counts definitions with the given status
    long countByStatus(JobStatus status);
}
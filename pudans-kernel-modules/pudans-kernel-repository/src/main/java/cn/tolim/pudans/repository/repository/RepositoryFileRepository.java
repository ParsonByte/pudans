package cn.tolim.pudans.repository.repository;

import cn.tolim.pudans.repository.entity.RepositoryFileEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RepositoryFileRepository extends JpaRepository<RepositoryFileEntity, String> {

    Optional<RepositoryFileEntity> findByIdAndTenantIdAndDeleteTimeIsNull(String id, String tenantId);

    // Search active files with optional filters
    @Query("""
            select file from RepositoryFileEntity file
            where file.tenantId = :tenantId
              and file.deleteTime is null
              and (:appCode is null or file.appCode = :appCode)
              and (:category is null or file.category = :category)
              and (:keyword is null or lower(file.originalName) like lower(concat('%', :keyword, '%')))
            """)
    Page<RepositoryFileEntity> search(@Param("tenantId") String tenantId,
                                      @Param("appCode") String appCode,
                                      @Param("category") String category,
                                      @Param("keyword") String keyword,
                                      Pageable pageable);
}
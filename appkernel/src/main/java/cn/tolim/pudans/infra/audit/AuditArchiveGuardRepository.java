package cn.tolim.pudans.infra.audit;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface AuditArchiveGuardRepository extends ReactiveCrudRepository<AuditArchiveGuardEntity, Integer> {}

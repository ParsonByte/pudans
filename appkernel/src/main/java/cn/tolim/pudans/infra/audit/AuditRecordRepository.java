package cn.tolim.pudans.infra.audit;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface AuditRecordRepository extends ReactiveCrudRepository<AuditRecordEntity, String> {}

package cn.tolim.pudans.core.infra.audit;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;


@Repository
public interface AuditRecordRepository extends ReactiveCrudRepository<AuditRecordEntity, String> {

    Mono<Boolean> existsByFingerprint(String fingerprint);
}

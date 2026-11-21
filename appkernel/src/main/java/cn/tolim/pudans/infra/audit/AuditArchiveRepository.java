package cn.tolim.pudans.infra.audit;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;


@Repository
public interface AuditArchiveRepository extends ReactiveCrudRepository<AuditArchiveEntity, String> {

    // 判断归档表中是否已存在相同指纹的记录。
    Mono<Boolean> existsByFingerprint(String fingerprint);
}

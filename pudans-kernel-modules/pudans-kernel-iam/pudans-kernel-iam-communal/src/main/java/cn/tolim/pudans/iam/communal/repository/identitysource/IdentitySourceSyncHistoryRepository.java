package cn.tolim.pudans.iam.communal.repository.identitysource;

import cn.tolim.pudans.iam.communal.entity.identitysource.IdentitySourceSyncHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;


@Repository
public interface IdentitySourceSyncHistoryRepository extends
        JpaRepository<IdentitySourceSyncHistoryEntity, String>,
        JpaSpecificationExecutor<IdentitySourceSyncHistoryEntity> {
}

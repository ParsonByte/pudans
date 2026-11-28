package cn.tolim.pudans.iam.communal.repository.identitysource;

import cn.tolim.pudans.iam.communal.entity.identitysource.IdentitySourceSyncRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;


@Repository
public interface IdentitySourceSyncRecordRepository extends
        JpaRepository<IdentitySourceSyncRecordEntity, String>,
        JpaSpecificationExecutor<IdentitySourceSyncRecordEntity>,
        IdentitySourceSyncRecordRepositoryCustomized {
}

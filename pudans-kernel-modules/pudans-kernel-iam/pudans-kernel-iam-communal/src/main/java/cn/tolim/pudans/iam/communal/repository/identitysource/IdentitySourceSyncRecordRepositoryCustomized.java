package cn.tolim.pudans.iam.communal.repository.identitysource;

import cn.tolim.pudans.iam.communal.entity.identitysource.IdentitySourceSyncRecordEntity;

import java.util.List;


public interface IdentitySourceSyncRecordRepositoryCustomized {
    // Batch save
    // Parameter list: List
    void batchSave(List<IdentitySourceSyncRecordEntity> list);
}

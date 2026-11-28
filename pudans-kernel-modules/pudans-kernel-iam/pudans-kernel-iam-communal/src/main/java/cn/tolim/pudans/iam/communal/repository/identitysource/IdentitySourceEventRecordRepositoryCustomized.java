package cn.tolim.pudans.iam.communal.repository.identitysource;

import cn.tolim.pudans.iam.communal.entity.identitysource.IdentitySourceEventRecordEntity;

import java.util.List;


public interface IdentitySourceEventRecordRepositoryCustomized {
    // Batch save
    // Parameter list: List
    void batchSave(List<IdentitySourceEventRecordEntity> list);
}

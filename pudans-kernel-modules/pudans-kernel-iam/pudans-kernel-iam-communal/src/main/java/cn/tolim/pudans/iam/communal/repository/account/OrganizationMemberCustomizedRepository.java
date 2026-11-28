package cn.tolim.pudans.iam.communal.repository.account;

import cn.tolim.pudans.iam.communal.entity.account.OrganizationMemberEntity;

import java.util.List;


public interface OrganizationMemberCustomizedRepository {

    // Saves organization members in a batch.
    void batchSave(List<OrganizationMemberEntity> list);
}

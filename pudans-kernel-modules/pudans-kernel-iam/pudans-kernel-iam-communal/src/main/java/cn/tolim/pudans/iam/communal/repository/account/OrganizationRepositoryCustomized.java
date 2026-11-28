package cn.tolim.pudans.iam.communal.repository.account;

import cn.tolim.pudans.iam.communal.entity.account.OrganizationEntity;
import cn.tolim.pudans.iam.communal.entity.account.po.OrganizationPO;

import java.util.List;


public interface OrganizationRepositoryCustomized {

    // Batch save
    // Parameter list: List
    void batchSave(List<OrganizationEntity> list);

    // Batch update
    // Parameter list: List
    void batchUpdate(List<OrganizationEntity> list);

    // Organizations for a user
    // Parameter userId: String
    // Returns: List
    List<OrganizationPO> getOrganizationList(String userId);
}

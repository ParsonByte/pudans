package cn.tolim.pudans.iam.communal.repository.account;

import cn.tolim.pudans.iam.communal.entity.account.UserEntity;
import cn.tolim.pudans.iam.communal.entity.account.po.UserPO;
import cn.tolim.pudans.iam.communal.entity.account.query.UserListNotInGroupQuery;
import cn.tolim.pudans.iam.communal.entity.account.query.UserListQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;


public interface UserRepositoryCustomized {
    // Get user list
    // Parameter pageable: Pageable
    // Parameter query: UserListQuery
    // Returns: Page
    Page<UserPO> getUserList(UserListQuery query, Pageable pageable);

    // Get users not in the user group
    // Parameter query: UserListNotInGroupQuery
    // Parameter pageable: Pageable
    // Returns: Page
    Page<UserPO> getUserListNotInGroupId(UserListNotInGroupQuery query, Pageable pageable);

    // Find users outside an organization and by data source
    // Parameter identitySourceId: String
    // Returns: List
    List<UserEntity> findAllByOrgIdNotExistAndIdentitySourceId(String identitySourceId);

    // Batch create
    // Parameter list: List
    void batchSave(List<UserEntity> list);

    // Batch update
    // Parameter list: List
    void batchUpdate(List<UserEntity> list);
}

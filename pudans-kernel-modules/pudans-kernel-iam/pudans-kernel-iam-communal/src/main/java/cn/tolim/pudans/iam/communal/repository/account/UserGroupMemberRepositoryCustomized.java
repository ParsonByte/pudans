package cn.tolim.pudans.iam.communal.repository.account;

import cn.tolim.pudans.iam.communal.entity.account.po.UserPO;
import cn.tolim.pudans.iam.communal.entity.account.query.UserGroupMemberListQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public interface UserGroupMemberRepositoryCustomized {
    // Get user group member list
    // Parameter query: UserGroupMemberListQuery
    // Parameter pageable: Pageable
    // Returns: Page
    Page<UserPO> getUserGroupMemberList(UserGroupMemberListQuery query, Pageable pageable);
}

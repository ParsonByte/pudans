package cn.tolim.pudans.iam.communal.repository.account;

import cn.tolim.pudans.iam.communal.entity.account.UserDetailEntity;

import java.util.ArrayList;
import java.util.List;


public interface UserDetailRepositoryCustomized {
    // Batch create
    // Parameter list: List
    void batchSave(List<UserDetailEntity> list);

    // Batch update
    // Parameter list: List
    void batchUpdate(ArrayList<UserDetailEntity> list);
}

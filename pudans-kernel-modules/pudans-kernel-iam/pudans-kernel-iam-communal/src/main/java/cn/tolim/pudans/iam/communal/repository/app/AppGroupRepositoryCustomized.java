package cn.tolim.pudans.iam.communal.repository.app;

import cn.tolim.pudans.iam.communal.entity.app.po.AppGroupPO;
import cn.tolim.pudans.iam.communal.entity.app.query.AppGroupQueryParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;


public interface AppGroupRepositoryCustomized {

    // Returns a page of application groups matching the query.
    Page<AppGroupPO> getAppGroupList(AppGroupQueryParam query, Pageable pageable);

    // Returns application groups visible to the given subject IDs.
    List<AppGroupPO> getAppGroupList(List<String> subjectIds, AppGroupQueryParam query);

    // Returns the number of applications visible to the subjects in the group.
    Long getAppCount(List<String> subjectIds, String groupId);
}

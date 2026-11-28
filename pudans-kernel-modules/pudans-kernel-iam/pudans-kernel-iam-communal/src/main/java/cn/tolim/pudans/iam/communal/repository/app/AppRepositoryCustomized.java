package cn.tolim.pudans.iam.communal.repository.app;

import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.communal.entity.app.po.AppPO;
import cn.tolim.pudans.iam.communal.entity.app.query.GetAppListQueryParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;


public interface AppRepositoryCustomized {
    // Returns applications visible to the given subject IDs.
    List<AppPO> getAppList(List<String> subjectIds);

    // Returns a filtered page of applications visible to the given subject IDs.
    Page<AppEntity> getAppList(List<String> subjectIds, GetAppListQueryParam query,
                               Pageable pageable);

    // Returns the number of applications visible to the given subject IDs.
    Long getAppCount(List<String> subjectIds);
}

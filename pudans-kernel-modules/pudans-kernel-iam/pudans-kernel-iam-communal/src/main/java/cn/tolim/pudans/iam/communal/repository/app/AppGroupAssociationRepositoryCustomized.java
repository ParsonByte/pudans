package cn.tolim.pudans.iam.communal.repository.app;

import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.communal.entity.app.query.AppGroupAssociationListQueryParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public interface AppGroupAssociationRepositoryCustomized {

    // Get application group application list
    // Parameter query: AppGroupAssociationListQueryParam
    // Parameter pageable: Pageable
    // Returns: Page
    Page<AppEntity> getAppGroupAssociationList(AppGroupAssociationListQueryParam query,
                                               Pageable pageable);
}

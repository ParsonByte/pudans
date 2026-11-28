package cn.tolim.pudans.iam.communal.repository.app;

import cn.tolim.pudans.iam.communal.entity.app.po.AppAccessPolicyPO;
import cn.tolim.pudans.iam.communal.entity.app.query.AppAccessPolicyQueryParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public interface AppAccessPolicyRepositoryCustomized {

    // Get application access policy list
    // Parameter pageable: Pageable
    // Parameter query: AppAccessPolicyQueryParam
    // Returns: Page
    Page<AppAccessPolicyPO> getAppPolicyList(AppAccessPolicyQueryParam query, Pageable pageable);
}

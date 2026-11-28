package cn.tolim.pudans.iam.communal.repository.app;

import cn.tolim.pudans.iam.communal.entity.app.po.AppAccountPO;
import cn.tolim.pudans.iam.communal.entity.app.query.AppAccountQueryParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public interface AppAccountRepositoryCustomized {

    // Get application account list
    // Parameter pageable: Pageable
    // Parameter query: AppAccountQueryParam
    // Returns: Page
    Page<AppAccountPO> getAppAccountList(AppAccountQueryParam query, Pageable pageable);
}

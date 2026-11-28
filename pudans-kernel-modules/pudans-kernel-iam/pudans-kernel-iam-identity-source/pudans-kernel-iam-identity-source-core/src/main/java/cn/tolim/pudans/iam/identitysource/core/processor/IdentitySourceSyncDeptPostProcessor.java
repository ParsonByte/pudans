package cn.tolim.pudans.iam.identitysource.core.processor;

import cn.tolim.pudans.iam.communal.enums.TriggerType;
import cn.tolim.pudans.iam.identitysource.core.domain.Dept;

import java.time.LocalDateTime;
import java.util.List;


public interface IdentitySourceSyncDeptPostProcessor {
    // Process data
    // Parameter batch String
    // Parameter identitySourceId String
    // Parameter deptList List
    // Parameter startTime LocalDateTime
    // Parameter triggerType TriggerType
    void process(String batch, String identitySourceId, List<Dept> deptList,
                 LocalDateTime startTime, TriggerType triggerType);

}

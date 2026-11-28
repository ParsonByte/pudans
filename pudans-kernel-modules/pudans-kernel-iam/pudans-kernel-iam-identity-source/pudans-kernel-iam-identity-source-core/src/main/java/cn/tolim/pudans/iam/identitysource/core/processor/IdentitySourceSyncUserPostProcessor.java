package cn.tolim.pudans.iam.identitysource.core.processor;

import cn.tolim.pudans.iam.communal.enums.TriggerType;
import cn.tolim.pudans.iam.identitysource.core.domain.User;

import java.time.LocalDateTime;
import java.util.List;


public interface IdentitySourceSyncUserPostProcessor {
    // Process data
    // Parameter batch String
    // Parameter identitySourceId String
    // Parameter userList List
    // Parameter startTime LocalDateTime
    // Parameter triggerType TriggerType
    void process(String batch, String identitySourceId, List<User> userList,
                 LocalDateTime startTime, TriggerType triggerType);

}

package cn.tolim.pudans.iam.identitysource.core;

import cn.tolim.pudans.iam.communal.enums.TriggerType;
import jakarta.servlet.http.HttpServletRequest;


public interface IdentitySource<T extends IdentitySourceConfig> {
    // Get identity source ID
    // Returns String
    String getId();

    // Get identity source name
    // Returns String
    String getName();

    // Get identity source configuration
    // Returns String
    T getConfig();

    // Sync
    // Parameter triggerType TriggerType Execution mode
    void sync(TriggerType triggerType);

    // Callback
    // Parameter request HttpServletRequest
    // Parameter body String
    // Returns Map
    Object event(HttpServletRequest request, String body);

}

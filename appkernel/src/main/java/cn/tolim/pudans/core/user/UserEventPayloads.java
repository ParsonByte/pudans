package cn.tolim.pudans.core.user;

import cn.tolim.pudans.contract.user.User;
import java.util.LinkedHashMap;
import java.util.Map;

public final class UserEventPayloads {

    private UserEventPayloads() {}

    public static Map<String, Object> created(User user) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("username", user.getMetadata().getName());
        payload.put("uid", user.getSpec().getUid());
        payload.put("displayName", user.getSpec().getDisplayName());
        payload.put("email", user.getSpec().getEmail());
        return payload;
    }
}

package cn.tolim.pudans.iam.core.auth.session;

import java.time.Duration;

public interface SessionCache {

    // Caches an active global login session marker
    // Parameter sid: String
    // Parameter ttl: Duration
    void put(String sid, Duration ttl);

    // Removes a global login session marker
    // Parameter sid: String
    void evict(String sid);

    // Checks whether the marker is present
    // Parameter sid: String
    // Returns: boolean
    boolean contains(String sid);
}
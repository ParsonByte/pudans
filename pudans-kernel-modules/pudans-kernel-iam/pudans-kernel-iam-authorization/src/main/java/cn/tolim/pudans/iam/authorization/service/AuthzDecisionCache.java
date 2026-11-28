package cn.tolim.pudans.iam.authorization.service;

import cn.tolim.pudans.iam.authorization.constant.AuthorizationConstants;
import cn.tolim.pudans.iam.authorization.dto.PolicyEvaluateResponse;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;


@Component
public class AuthzDecisionCache {

    private final Map<String, CacheEntry> entries = new ConcurrentHashMap<>();

    // Returns a cached decision when it is present and still valid
    // Parameter key: String cache key
    // Returns: Optional<PolicyEvaluateResponse>
    public Optional<PolicyEvaluateResponse> get(String key) {
        if (key == null) {
            return Optional.empty();
        }
        CacheEntry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAtMillis() < System.currentTimeMillis()) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.response());
    }

    // Stores a decision for a short period
    // Parameter key: String cache key
    // Parameter response: PolicyEvaluateResponse decision
    public void put(String key, PolicyEvaluateResponse response) {
        if (key == null || response == null) {
            return;
        }
        long expiresAt = System.currentTimeMillis()
                + AuthorizationConstants.DECISION_CACHE_TTL_SECONDS * 1000L;
        entries.put(key, new CacheEntry(expiresAt, response));
    }

    // Invalidates every cached decision after a policy or permission change
    public void invalidateAll() {
        entries.clear();
    }

    // Returns the number of cached decisions
    // Returns: int
    public int size() {
        return entries.size();
    }

    private record CacheEntry(long expiresAtMillis, PolicyEvaluateResponse response) {
    }
}
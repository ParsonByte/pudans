package cn.tolim.pudans.iam.protocol.saml.store;

import cn.tolim.pudans.iam.protocol.saml.model.SamlAuthnRequest;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


public class InMemorySamlAuthnRequestStore implements SamlAuthnRequestStore {

    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public void save(SamlAuthnRequest authnRequest, Duration timeToLive) {
        Assert.notNull(authnRequest, "authnRequest must not be null");
        Assert.hasText(authnRequest.getId(), "authnRequest id must not be blank");
        Duration ttl = timeToLive == null ? Duration.ofMinutes(5) : timeToLive;
        entries.put(authnRequest.getId(), new Entry(authnRequest, Instant.now().plus(ttl)));
        removeExpired();
    }

    @Override
    public SamlAuthnRequest find(String id) {
        if (id == null) {
            return null;
        }
        Entry entry = entries.get(id);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            entries.remove(id, entry);
            return null;
        }
        return entry.request();
    }

    @Override
    public SamlAuthnRequest consume(String id) {
        if (id == null) {
            return null;
        }
        Entry entry = entries.remove(id);
        if (entry == null || entry.isExpired()) {
            return null;
        }
        return entry.request();
    }

    @Override
    public void remove(String id) {
        if (id != null) {
            entries.remove(id);
        }
    }

    private void removeExpired() {
        Instant now = Instant.now();
        entries.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private record Entry(SamlAuthnRequest request, Instant expiresAt) {

        boolean isExpired() {
            return !expiresAt.isAfter(Instant.now());
        }
    }
}
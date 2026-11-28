package cn.tolim.pudans.iam.identitysource.core.event;


public interface IdentitySourceEventListener {
    // Register
    // Parameter id String
    void register(String id);

    // Register
    // Parameter id String
    void destroy(String id);

    // Sync
    // Parameter id String
    void sync(String id);
}

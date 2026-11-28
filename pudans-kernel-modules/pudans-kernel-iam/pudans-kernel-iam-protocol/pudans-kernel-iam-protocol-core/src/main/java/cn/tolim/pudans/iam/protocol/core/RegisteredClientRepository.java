package cn.tolim.pudans.iam.protocol.core;

import org.springframework.lang.Nullable;


public interface RegisteredClientRepository<T extends RegisteredClient> {
    // Save client
    // Parameter client T
    void save(T client);

    // Query registered client by ID
    // Parameter id String
    // Returns T
    @Nullable
    T findById(String id);

    // Query registered client by client ID
    // Parameter clientId String
    // Returns T
    @Nullable
    T findByClientId(String clientId);

}

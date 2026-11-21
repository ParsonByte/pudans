package cn.tolim.pudans.core.storage.driver;

import cn.tolim.pudans.contract.storage.resource.StoragePolicyType;
import java.util.Set;

public record StoragePolicyTypeDescriptor(
        StoragePolicyType type,
        String displayName,
        String description,
        boolean credentialsRequired,
        Set<StorageCapability> capabilities) {}

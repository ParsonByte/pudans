package cn.tolim.pudans.core.storage.driver;

import cn.tolim.pudans.contract.storage.resource.StoragePolicy;

public record ResolvedStoragePolicy(StoragePolicy policy, StoragePolicy.Credentials credentials) {}

package cn.tolim.pudans.core.storage.driver;

import cn.tolim.pudans.contract.storage.StoragePolicy;

public record ResolvedStoragePolicy(StoragePolicy policy, StoragePolicy.Credentials credentials) {}

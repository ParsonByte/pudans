package cn.tolim.pudans.core.storage.driver;

import cn.tolim.pudans.contract.core.extension.storage.StoragePolicy;

public record ResolvedStoragePolicy(StoragePolicy policy, StoragePolicy.Credentials credentials) {}

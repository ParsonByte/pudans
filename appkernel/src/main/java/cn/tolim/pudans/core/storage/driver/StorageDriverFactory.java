package cn.tolim.pudans.core.storage.driver;

import java.util.List;

public interface StorageDriverFactory {

    List<StoragePolicyTypeDescriptor> descriptors();

    StorageDriver create(ResolvedStoragePolicy policy);
}

package cn.tolim.pudans.core.storage.driver;

import java.util.List;

public interface StorageDriverFactory {

    // 返回工厂支持的存储类型描述。
    List<StoragePolicyTypeDescriptor> descriptors();

    // 按已解析策略创建存储驱动实例。
    StorageDriver create(ResolvedStoragePolicy policy);
}

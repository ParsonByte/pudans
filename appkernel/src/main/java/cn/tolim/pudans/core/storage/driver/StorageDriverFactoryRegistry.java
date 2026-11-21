package cn.tolim.pudans.core.storage.driver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import cn.tolim.pudans.contract.storage.resource.StoragePolicyType;

@Component
public class StorageDriverFactoryRegistry {

    private final Map<StoragePolicyType, StorageDriverFactory> factories;

    private final List<StoragePolicyTypeDescriptor> descriptors;

    public StorageDriverFactoryRegistry(List<StorageDriverFactory> driverFactories) {
        var factoryMap = new EnumMap<StoragePolicyType, StorageDriverFactory>(StoragePolicyType.class);
        var descriptorList = new ArrayList<StoragePolicyTypeDescriptor>();
        for (var factory : driverFactories) {
            for (var descriptor : factory.descriptors()) {
                if (factoryMap.putIfAbsent(descriptor.type(), factory) != null) {
                    throw new IllegalStateException("存储策略类型重复注册：" + descriptor.type());
                }
                descriptorList.add(descriptor);
            }
        }
        descriptorList.sort(Comparator.comparingInt(descriptor -> descriptor.type().ordinal()));
        this.factories = Map.copyOf(factoryMap);
        this.descriptors = List.copyOf(descriptorList);
    }

    public Optional<StorageDriverFactory> factory(StoragePolicyType type) {
        return Optional.ofNullable(factories.get(type));
    }

    public List<StoragePolicyTypeDescriptor> descriptors() {
        return descriptors;
    }
}

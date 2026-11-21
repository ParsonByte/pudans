package cn.tolim.pudans.core.infra.properties;

import lombok.Data;

@Data
public class StorageProperties {

    private long cleanupIntervalSeconds = 300;
}

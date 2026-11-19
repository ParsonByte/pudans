package cn.tolim.pudans.core.storage.driver;

import java.time.Duration;

public record StorageSourceOptions(boolean download, String displayName, Duration ttl) {}

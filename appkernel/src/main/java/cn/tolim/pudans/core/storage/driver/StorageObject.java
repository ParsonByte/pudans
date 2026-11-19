package cn.tolim.pudans.core.storage.driver;

import java.time.Instant;

public record StorageObject(String key, long size, Instant lastModified, String etag) {}

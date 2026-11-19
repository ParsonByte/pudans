package cn.tolim.pudans.core.storage;

import java.time.Instant;

public record StoragePathContext(String owner, String originPath, String originName, Instant now) {}

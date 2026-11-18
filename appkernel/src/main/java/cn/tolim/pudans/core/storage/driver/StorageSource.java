package cn.tolim.pudans.core.storage.driver;

import java.net.URI;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record StorageSource(
        @Nullable URI url, @Nullable Instant expiresAt, boolean proxyRequired) {}

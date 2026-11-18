package cn.tolim.pudans.core.storage.download;

import java.time.Instant;

public record DownloadUrl(String url, Instant expiresAt) {}

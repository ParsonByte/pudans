package cn.tolim.pudans.contract.live.dto;

import java.time.Instant;

public record PartDownload(
        int index, String filename, String url, Instant expiresAt,
        boolean proxyRequired, long size) {}

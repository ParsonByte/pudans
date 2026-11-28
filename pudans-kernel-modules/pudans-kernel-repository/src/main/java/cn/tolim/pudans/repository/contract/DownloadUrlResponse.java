package cn.tolim.pudans.repository.contract;

import java.time.LocalDateTime;

public record DownloadUrlResponse(String url, LocalDateTime expiresAt, String provider, boolean direct) {
}
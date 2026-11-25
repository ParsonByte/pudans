package cn.tolim.pudans.contract.creation.dto;

import java.time.Instant;

public record WorkPlay(String workId, String storageFileId, String downloadUrl, Instant expiresAt) {}

package cn.tolim.pudans.repository.storage;

// Object metadata reported by a storage provider
public record StorageObjectStat(String objectKey, long size, String contentType, String etag) {
}
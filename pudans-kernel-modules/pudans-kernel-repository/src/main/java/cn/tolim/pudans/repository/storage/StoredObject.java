package cn.tolim.pudans.repository.storage;

// Result of a stored object write
public record StoredObject(String objectKey, long size, String etag) {
}
package cn.tolim.pudans.iam.communal.storage;

import java.io.InputStream;

// Stored file content streamed through the storage backend
public record StorageContent(InputStream content, String contentType, long size, String fileName) {
}
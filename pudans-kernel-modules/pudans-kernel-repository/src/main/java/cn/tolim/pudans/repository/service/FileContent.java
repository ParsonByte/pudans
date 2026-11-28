package cn.tolim.pudans.repository.service;

import cn.tolim.pudans.repository.entity.RepositoryFileEntity;

import java.io.InputStream;

// Metadata plus an open content stream; the caller must close the stream
public record FileContent(RepositoryFileEntity file, InputStream content) {
}
package cn.tolim.pudans.search.repository;

import cn.tolim.pudans.search.entity.SearchDocumentEntity;

import java.util.List;

public record SearchDocumentPage(List<SearchDocumentEntity> documents, long total) {
}
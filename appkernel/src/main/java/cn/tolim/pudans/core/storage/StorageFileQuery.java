package cn.tolim.pudans.core.storage;

import cn.tolim.pudans.contract.storage.StorageFileState;

public record StorageFileQuery(
        String policy,
        String owner,
        String module,
        StorageFileState state,
        String extension,
        int page,
        int size) {

    // 约束分页参数范围，避免越界查询。
    public StorageFileQuery {
        page = page < 1 ? 1 : page;
        size = size < 1 ? 20 : Math.min(size, 1000);
    }
}

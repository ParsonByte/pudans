package cn.tolim.pudans.core.creation.task;

import cn.tolim.pudans.contract.creation.GenerationTask;
import cn.tolim.pudans.contract.storage.StorageFile;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.util.List;
import reactor.core.publisher.Mono;

public interface GenerationResultAssets {

    // P4 实现逻辑资产登记，返回真实资产标识。
    Mono<List<String>> register(
            RequestIdentity identity, GenerationTask task, List<StorageFile> storageFiles);
}

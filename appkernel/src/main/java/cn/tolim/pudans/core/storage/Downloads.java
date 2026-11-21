package cn.tolim.pudans.core.storage;

import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import cn.tolim.pudans.contract.storage.resource.StorageFile;
import cn.tolim.pudans.contract.storage.resource.StorageFileState;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.core.storage.driver.StorageCapability;
import cn.tolim.pudans.core.storage.driver.StorageDriverResolver;
import cn.tolim.pudans.core.storage.driver.StorageReadOptions;
import cn.tolim.pudans.core.storage.driver.StorageReadResult;
import cn.tolim.pudans.core.storage.driver.StorageSource;
import cn.tolim.pudans.core.storage.driver.StorageSourceOptions;
import cn.tolim.pudans.core.storage.download.DownloadUrl;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.setting.ParameterGroups;
import reactor.core.publisher.Mono;

@Service
public class Downloads {

    private final FileRecords fileRecords;

    private final StorageDriverResolver driverResolver;

    private final TransferTokens transferTokens;

    private final ParameterGroups parameterGroups;

    private final Policies policies;

    public Downloads(
            FileRecords fileRecords,
            StorageDriverResolver driverResolver,
            TransferTokens transferTokens,
            ParameterGroups parameterGroups,
            Policies policies) {
        this.fileRecords = fileRecords;
        this.driverResolver = driverResolver;
        this.transferTokens = transferTokens;
        this.parameterGroups = parameterGroups;
        this.policies = policies;
    }

    public Mono<DownloadUrl> downloadUrl(String fileId, Duration requestedTtl) {
        return fileRecords.get(fileId).flatMap(file -> {
            ensureActive(file);
            return parameterGroups
                    .getStorage()
                    .map(settings -> {
                        var defaultTtl = Duration.ofSeconds(
                                settings.getDownloadTokenTtlSeconds() == null
                                        ? 300
                                        : settings.getDownloadTokenTtlSeconds());
                        var ttl = requestedTtl == null
                                        || requestedTtl.isZero()
                                        || requestedTtl.isNegative()
                                ? defaultTtl
                                : requestedTtl;
                        if (ttl.compareTo(defaultTtl) > 0) {
                            ttl = defaultTtl;
                        }
                        var expiresAt = Instant.now().plus(ttl);
                        var token = transferTokens.signDownload(fileId, ttl);
                        var url = ApiGroups.API_PREFIX
                                + "/storagefiles/"
                                + fileId
                                + "/content?token="
                                + token;
                        return new DownloadUrl(url, expiresAt);
                    });
        });
    }

    public Mono<StorageReadResult> open(String fileId, StorageReadOptions options) {
        return fileRecords.get(fileId).flatMap(file -> {
            ensureActive(file);
            return driverResolver
                    .driver(file.getSpec().getPolicy())
                    .flatMap(driver -> driver.get(file.getSpec().getStorageKey(), options));
        });
    }

    public Mono<StorageSource> sourceUrl(String fileId, StorageSourceOptions options) {
        return fileRecords.get(fileId).flatMap(file -> {
            ensureActive(file);
            return policies.get(file.getSpec().getPolicy()).flatMap(policy -> {
                if (policy.getSpec().isProxyDownload()) {
                    return Mono.empty();
                }
                return driverResolver.driver(file.getSpec().getPolicy()).flatMap(driver -> {
                    if (!driver.capabilities().contains(StorageCapability.PRESIGNED_SOURCE)) {
                        return Mono.empty();
                    }
                    return driver.source(file.getSpec().getStorageKey(), options)
                            .filter(source -> !source.proxyRequired() && source.url() != null);
                });
            });
        });
    }

    public void verify(String token, String fileId) {
        transferTokens.verifyDownload(token, fileId);
    }

    private void ensureActive(StorageFile file) {
        if (file.getSpec().getState() != StorageFileState.ACTIVE) {
            throw new ApiException(HttpStatus.NOT_FOUND, "storage-file-not-found", "文件不存在。");
        }
    }
}

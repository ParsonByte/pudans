package cn.tolim.pudans.core.storage;

import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import cn.tolim.pudans.contract.core.extension.storage.StorageFile;
import cn.tolim.pudans.contract.core.extension.storage.StorageFileState;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.core.storage.driver.StorageCapability;
import cn.tolim.pudans.core.storage.driver.StorageDriverResolver;
import cn.tolim.pudans.core.storage.driver.StorageReadOptions;
import cn.tolim.pudans.core.storage.driver.StorageReadResult;
import cn.tolim.pudans.core.storage.driver.StorageSource;
import cn.tolim.pudans.core.storage.driver.StorageSourceOptions;
import cn.tolim.pudans.core.storage.download.DownloadUrl;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.setting.ParameterSettingGroupService;
import reactor.core.publisher.Mono;

@Service
public class StorageDownloadService {

    private final StorageFileService fileService;

    private final StorageDriverResolver driverResolver;

    private final StorageTokenService tokenService;

    private final ParameterSettingGroupService parameterSettingGroupService;

    private final StoragePolicyService policyService;

    public StorageDownloadService(
            StorageFileService fileService,
            StorageDriverResolver driverResolver,
            StorageTokenService tokenService,
            ParameterSettingGroupService parameterSettingGroupService,
            StoragePolicyService policyService) {
        this.fileService = fileService;
        this.driverResolver = driverResolver;
        this.tokenService = tokenService;
        this.parameterSettingGroupService = parameterSettingGroupService;
        this.policyService = policyService;
    }

    public Mono<DownloadUrl> downloadUrl(String fileId, Duration requestedTtl) {
        return fileService.get(fileId).flatMap(file -> {
            ensureActive(file);
            return parameterSettingGroupService
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
                        var token = tokenService.signDownload(fileId, ttl);
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
        return fileService.get(fileId).flatMap(file -> {
            ensureActive(file);
            return driverResolver
                    .driver(file.getSpec().getPolicy())
                    .flatMap(driver -> driver.get(file.getSpec().getStorageKey(), options));
        });
    }

    public Mono<StorageSource> sourceUrl(String fileId, StorageSourceOptions options) {
        return fileService.get(fileId).flatMap(file -> {
            ensureActive(file);
            return policyService.get(file.getSpec().getPolicy()).flatMap(policy -> {
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
        tokenService.verifyDownload(token, fileId);
    }

    private void ensureActive(StorageFile file) {
        if (file.getSpec().getState() != StorageFileState.ACTIVE) {
            throw new ApiException(HttpStatus.NOT_FOUND, "storage-file-not-found", "文件不存在。");
        }
    }
}

import { axiosInstance } from "@pudans-dev/api-client";

import type { ExtensionList, ExtensionMeta } from "./_extension";

const API_ROOT = "/apis/api.pudans.tolim.cn/v1alpha1";

export type StoragePolicyType = "local" | "s3" | "ks3" | "oss" | "cos" | "obs" | "qiniu" | "upyun" | "onedrive";

export type StoragePolicyTypeDescriptor = {
    capabilities?: string[];
    credentialsRequired: boolean;
    description: string;
    displayName: string;
    type: StoragePolicyType;
};

export type StoragePolicyValidationResult = {
    errors: string[];
    valid: boolean;
};

export type StoragePolicy = {
    apiVersion: string;
    kind: string;
    metadata: ExtensionMeta;
    spec: {
        bucketName?: string;
        chunkSize?: number;
        credentials?: {
            accessKey?: string;
            clientSecret?: string;
            secretKey?: string;
            token?: string;
        };
        defaultPolicy?: boolean;
        description?: string;
        displayName?: string;
        dirNameRule?: string;
        fileNameRule?: string;
        fileTypeDenyList?: boolean;
        fileTypes?: string[];
        maxSize?: number;
        nameRegexp?: string;
        nameRegexpDenyList?: boolean;
        privateBucket?: boolean;
        proxyDownload?: boolean;
        server?: string;
        settings?: {
            deleteBatchSize?: number;
            driveId?: string;
            region?: string;
            s3PathStyle?: boolean;
            tenantId?: string;
        };
        type?: StoragePolicyType;
    };
    status?: {
        credentialConfigured?: Record<string, boolean>;
        lastTestedAt?: string;
        message?: string;
        ready?: boolean;
    };
};

export type StorageFile = {
    apiVersion: string;
    kind: string;
    metadata: ExtensionMeta;
    spec: {
        contentType?: string;
        extension?: string;
        module?: string;
        originalName?: string;
        owner?: string;
        policy?: string;
        size?: number;
        state?: string;
        trashedAt?: string;
        trashExpiresAt?: string;
        uploadedAt?: string;
    };
};

// 存储资源由扩展路由提供，当前 OpenAPI 文档尚未生成对应客户端。
export async function listStoragePolicies(params: {
    page?: number;
    size?: number;
}): Promise<ExtensionList<StoragePolicy>> {
    return (await axiosInstance.get<ExtensionList<StoragePolicy>>(`${API_ROOT}/storagepolicies`, { params })).data;
}

export async function listStoragePolicyTypes(): Promise<StoragePolicyTypeDescriptor[]> {
    return (await axiosInstance.get<StoragePolicyTypeDescriptor[]>(`${API_ROOT}/storagepolicies/-/types`)).data;
}

export async function validateStoragePolicy(policy: StoragePolicy): Promise<StoragePolicyValidationResult> {
    return (await axiosInstance.post<StoragePolicyValidationResult>(`${API_ROOT}/storagepolicies/-/validate`, policy))
        .data;
}

export async function createStoragePolicy(policy: StoragePolicy): Promise<StoragePolicy> {
    return (await axiosInstance.post<StoragePolicy>(`${API_ROOT}/storagepolicies`, policy)).data;
}

export async function deleteStoragePolicy(name: string): Promise<void> {
    await axiosInstance.delete(`${API_ROOT}/storagepolicies/${encodeURIComponent(name)}`);
}

export async function listStorageFiles(params: {
    page?: number;
    size?: number;
    state?: "ACTIVE" | "TRASHED";
}): Promise<ExtensionList<StorageFile>> {
    return (
        await axiosInstance.get<ExtensionList<StorageFile>>(`${API_ROOT}/storagefiles`, {
            params: {
                page: params.page,
                size: params.size,
                fieldSelector: params.state ? [`spec.state=${params.state}`] : undefined,
            },
        })
    ).data;
}

export async function createStorageFileDownloadUrl(name: string): Promise<string> {
    const response = await axiosInstance.post<{ url?: string }>(
        `${API_ROOT}/storagefiles/${encodeURIComponent(name)}/download-url`,
    );

    return response.data.url ?? "";
}

export async function trashStorageFile(name: string): Promise<void> {
    await axiosInstance.post(`${API_ROOT}/storagefiles/${encodeURIComponent(name)}/trash`);
}

export async function restoreStorageFile(name: string): Promise<void> {
    await axiosInstance.post(`${API_ROOT}/storagefiles/${encodeURIComponent(name)}/restore`);
}

export async function purgeStorageFile(name: string): Promise<void> {
    await axiosInstance.delete(`${API_ROOT}/storagefiles/${encodeURIComponent(name)}/purge`);
}

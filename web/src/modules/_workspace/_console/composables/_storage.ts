import { axiosInstance } from "@pudans-dev/api-client";

const API_ROOT = "/apis/api.pudans.tolim.cn/v1alpha1";

type ExtensionMeta = {
    annotations?: Record<string, string>;
    creationTimestamp?: string;
    labels?: Record<string, string>;
    name: string;
};

export type ExtensionList<T> = {
    items?: T[];
    page?: number;
    size?: number;
    total?: number;
};

export type StoragePolicy = {
    apiVersion: string;
    kind: string;
    metadata: ExtensionMeta;
    spec: {
        bucketName?: string;
        defaultPolicy?: boolean;
        description?: string;
        displayName?: string;
        maxSize?: number;
        privateBucket?: boolean;
        server?: string;
        type?: string;
    };
    status?: {
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

export async function deleteStoragePolicy(name: string): Promise<void> {
    await axiosInstance.delete(`${API_ROOT}/storagepolicies/${encodeURIComponent(name)}`);
}

export async function listStorageFiles(params: { page?: number; size?: number }): Promise<ExtensionList<StorageFile>> {
    return (await axiosInstance.get<ExtensionList<StorageFile>>(`${API_ROOT}/storagefiles`, { params })).data;
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

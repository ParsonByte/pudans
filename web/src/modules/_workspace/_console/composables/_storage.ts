import { axiosInstance } from "@pudans-dev/api-client";

import { csrfHeaders } from "@/modules/_auth/composables/_auth-login";

import type { ExtensionList, ExtensionMeta } from "./_extension";

const API_ROOT = "/apis/api.pudans.tolim.cn/v1alpha1";

export type StoragePolicyType = "local" | "s3" | "ks3" | "oss" | "cos" | "obs" | "qiniu" | "upyun" | "onedrive";

export type StorageCapability =
    | "CORS_CONFIGURABLE"
    | "DIRECT_UPLOAD"
    | "MULTIPART_UPLOAD"
    | "PRESIGNED_PARTS"
    | "PRESIGNED_SOURCE"
    | "PROXY_REQUIRED"
    | "RANGE_READ";

export type StoragePolicyTypeDescriptor = {
    capabilities?: StorageCapability[];
    credentialsRequired: boolean;
    description: string;
    displayName: string;
    type: StoragePolicyType;
};

export type StoragePolicyValidationResult = {
    errors: string[];
    valid: boolean;
};

export type StoragePolicyTestResult = {
    message?: string;
    success: boolean;
    testedAt?: string;
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

export type StorageRoutePurpose = "creation_result" | "sandbox_snapshot";

export type StorageRoutePurposeDescriptor = {
    fallbackToDefaultPolicy: boolean;
    name: string;
    purpose: StorageRoutePurpose;
};

export type StorageRoute = {
    apiVersion: string;
    kind: string;
    metadata: ExtensionMeta;
    spec: {
        description?: string;
        enabled?: boolean;
        policyName?: string;
        purpose?: StorageRoutePurpose;
    };
};

export type StoragePolicyOAuthStatus = {
    authorized: boolean;
    message: string;
    policy: string;
};

export type StoragePolicyOAuthUrl = {
    redirectUri: string;
    state: string;
    url: string;
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

export type StorageUploadRequest = {
    contentType?: string;
    module?: string;
    originalName: string;
    policy: string;
    size: number;
};

export type StorageUploadDirectCredential = {
    contentType?: string;
    expiresAt?: string;
    key?: string;
    policy?: string;
    provider?: string;
    signature?: string;
    token?: string;
    uploadUrl?: string;
};

export type StorageUploadTicket = {
    chunkSize: number;
    directUpload?: StorageUploadDirectCredential;
    expiresAt: string;
    fileId: string;
    partCount: number;
    policy: string;
    presigned: boolean;
    storageKey: string;
    uploadToken: string;
};

export type StorageUploadedPart = {
    etag?: string;
    partNumber: number;
    size: number;
};

export type StorageUploadStatus = {
    chunkSize: number;
    expiresAt?: string;
    fileId: string;
    partCount: number;
    policy: string;
    presigned: boolean;
    storageKey: string;
    uploadedParts?: StorageUploadedPart[];
};

export type StoragePartUrl = {
    expiresAt?: string;
    partNumber: number;
    url: string;
};

export type StorageTransferTask<T> = {
    abort: () => void;
    promise: Promise<T>;
};

export type StorageTransferProgressHandler = (loaded: number, total: number) => void;

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

export async function updateStoragePolicy(name: string, policy: StoragePolicy): Promise<StoragePolicy> {
    return (await axiosInstance.put<StoragePolicy>(`${API_ROOT}/storagepolicies/${encodeURIComponent(name)}`, policy))
        .data;
}

export async function deleteStoragePolicy(name: string): Promise<void> {
    await axiosInstance.delete(`${API_ROOT}/storagepolicies/${encodeURIComponent(name)}`);
}

export async function getStoragePolicyOAuthStatus(name: string): Promise<StoragePolicyOAuthStatus> {
    return (
        await axiosInstance.get<StoragePolicyOAuthStatus>(
            `${API_ROOT}/storagepolicies/${encodeURIComponent(name)}/oauth/status`,
        )
    ).data;
}

export async function authorizeStoragePolicyOAuth(name: string): Promise<StoragePolicyOAuthUrl> {
    return (
        await axiosInstance.get<StoragePolicyOAuthUrl>(
            `${API_ROOT}/storagepolicies/${encodeURIComponent(name)}/oauth/authorize`,
        )
    ).data;
}

export async function resolveStoragePolicyOAuthRoot(name: string, url: string): Promise<string> {
    const response = await axiosInstance.get<{ driveId?: string }>(
        `${API_ROOT}/storagepolicies/${encodeURIComponent(name)}/oauth/root`,
        { params: { url } },
    );

    return response.data.driveId ?? "";
}

export async function listStorageRoutes(): Promise<StorageRoute[]> {
    return (await axiosInstance.get<StorageRoute[]>(`${API_ROOT}/storageroutes`)).data;
}

export async function listStorageRoutePurposes(): Promise<StorageRoutePurposeDescriptor[]> {
    return (await axiosInstance.get<StorageRoutePurposeDescriptor[]>(`${API_ROOT}/storageroutes/-/purposes`)).data;
}

export async function putStorageRoute(purpose: StorageRoutePurpose, spec: StorageRoute["spec"]): Promise<StorageRoute> {
    return (await axiosInstance.put<StorageRoute>(`${API_ROOT}/storageroutes/${encodeURIComponent(purpose)}`, spec))
        .data;
}

export async function deleteStorageRoute(purpose: StorageRoutePurpose): Promise<void> {
    await axiosInstance.delete(`${API_ROOT}/storageroutes/${encodeURIComponent(purpose)}`);
}

export async function testStorageRoute(purpose: StorageRoutePurpose): Promise<StoragePolicyTestResult> {
    return (
        await axiosInstance.post<StoragePolicyTestResult>(
            `${API_ROOT}/storageroutes/${encodeURIComponent(purpose)}/-/test`,
        )
    ).data;
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

export async function initiateStorageUpload(request: StorageUploadRequest): Promise<StorageUploadTicket> {
    return (await axiosInstance.post<StorageUploadTicket>(`${API_ROOT}/storagefiles/-/upload`, request)).data;
}

export async function issueStorageUploadToken(name: string): Promise<string> {
    const response = await axiosInstance.post<{ token?: string }>(
        `${API_ROOT}/storagefiles/${encodeURIComponent(name)}/upload/token`,
    );

    return response.data.token ?? "";
}

export async function getStorageUploadStatus(name: string, token: string): Promise<StorageUploadStatus> {
    return (
        await axiosInstance.get<StorageUploadStatus>(`${API_ROOT}/storagefiles/${encodeURIComponent(name)}/upload`, {
            params: { token },
        })
    ).data;
}

export async function presignStoragePart(name: string, token: string, partNumber: number): Promise<StoragePartUrl> {
    return (
        await axiosInstance.get<StoragePartUrl>(
            `${API_ROOT}/storagefiles/${encodeURIComponent(name)}/upload/parts/${partNumber}/url`,
            { params: { token } },
        )
    ).data;
}

export async function completeStorageUpload(
    name: string,
    token: string,
    parts: StorageUploadedPart[] = [],
): Promise<StorageFile> {
    return (
        await axiosInstance.post<StorageFile>(
            `${API_ROOT}/storagefiles/${encodeURIComponent(name)}/upload/complete`,
            parts,
            { params: { token } },
        )
    ).data;
}

export async function abortStorageUpload(name: string, token: string): Promise<void> {
    await axiosInstance.delete(`${API_ROOT}/storagefiles/${encodeURIComponent(name)}/upload`, {
        params: { token },
    });
}

export async function testStoragePolicy(name: string): Promise<StoragePolicyTestResult> {
    return (
        await axiosInstance.post<StoragePolicyTestResult>(
            `${API_ROOT}/storagepolicies/${encodeURIComponent(name)}/test`,
        )
    ).data;
}

export async function configureStoragePolicyCors(name: string, origins: string[]): Promise<void> {
    await axiosInstance.post(`${API_ROOT}/storagepolicies/${encodeURIComponent(name)}/cors`, { origins });
}

// 解析上传接口返回的错误描述，优先使用服务端详情。
function storageTransferError(xhr: XMLHttpRequest): Error {
    const text = xhr.responseText?.trim();
    if (text) {
        try {
            const payload = JSON.parse(text) as { detail?: string; error?: string; message?: string };
            const message = payload.detail ?? payload.message ?? payload.error;
            if (message) {
                return new Error(message);
            }
        } catch {
            return new Error(text);
        }
    }

    return new Error(`上传失败（HTTP ${xhr.status}）。`);
}

// 发送带进度的上传请求，返回可中止的任务句柄。
function storageUploadRequest<T>(options: {
    body: XMLHttpRequestBodyInit;
    contentType?: string;
    headers?: Record<string, string>;
    jsonResponse: boolean;
    method: "POST" | "PUT";
    onProgress?: StorageTransferProgressHandler;
    url: string;
}): StorageTransferTask<T> {
    const xhr = new XMLHttpRequest();
    const promise = new Promise<T>((resolve, reject) => {
        xhr.open(options.method, options.url);
        if (options.contentType) {
            xhr.setRequestHeader("Content-Type", options.contentType);
        }
        for (const [name, value] of Object.entries(options.headers ?? {})) {
            xhr.setRequestHeader(name, value);
        }
        if (options.onProgress) {
            const onProgress = options.onProgress;
            xhr.upload.onprogress = (event) => {
                if (event.lengthComputable) {
                    onProgress(event.loaded, event.total);
                }
            };
        }
        xhr.onload = () => {
            if (xhr.status < 200 || xhr.status >= 300) {
                reject(storageTransferError(xhr));
                return;
            }
            if (!options.jsonResponse) {
                resolve(undefined as T);
                return;
            }
            try {
                resolve(JSON.parse(xhr.responseText) as T);
            } catch {
                reject(new Error("上传响应解析失败。"));
            }
        };
        xhr.onerror = () => reject(new Error("网络异常，上传请求失败。"));
        xhr.onabort = () => reject(new DOMException("上传已中止。", "AbortError"));
        xhr.send(options.body);
    });

    return { abort: () => xhr.abort(), promise };
}

export function uploadStoragePart(
    name: string,
    token: string,
    partNumber: number,
    body: Blob,
    onProgress?: StorageTransferProgressHandler,
): StorageTransferTask<StorageUploadedPart> {
    return storageUploadRequest<StorageUploadedPart>({
        body,
        contentType: "application/octet-stream",
        headers: csrfHeaders(),
        jsonResponse: true,
        method: "PUT",
        onProgress,
        url: `${API_ROOT}/storagefiles/${encodeURIComponent(name)}/upload/parts/${partNumber}?token=${encodeURIComponent(token)}`,
    });
}

export function uploadPresignedStoragePart(
    url: string,
    body: Blob,
    onProgress?: StorageTransferProgressHandler,
): StorageTransferTask<void> {
    return storageUploadRequest<void>({
        body,
        jsonResponse: false,
        method: "PUT",
        onProgress,
        url,
    });
}

export function uploadDirectStorageFile(
    credential: StorageUploadDirectCredential,
    file: File,
    onProgress?: StorageTransferProgressHandler,
): StorageTransferTask<void> {
    const uploadUrl = credential.uploadUrl ?? "";
    const provider = credential.provider ?? "";
    if (!uploadUrl || (provider !== "qiniu" && provider !== "upyun")) {
        return { abort: () => {}, promise: Promise.reject(new Error("当前存储类型不支持浏览器直传。")) };
    }
    const formData = new FormData();
    if (provider === "qiniu") {
        formData.append("token", credential.token ?? "");
        formData.append("key", credential.key ?? "");
    } else {
        formData.append("policy", credential.policy ?? "");
        formData.append("signature", credential.signature ?? "");
    }
    formData.append("file", file);

    return storageUploadRequest<void>({
        body: formData,
        jsonResponse: false,
        method: "POST",
        onProgress,
        url: uploadUrl,
    });
}

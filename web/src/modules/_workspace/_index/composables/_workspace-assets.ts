import { type Asset, axiosInstance } from "@pudans-dev/api-client";

import { csrfHeaders } from "@/modules/_auth/composables/_auth-login";
import {
    type StorageFile,
    abortStorageUpload,
    completeStorageUpload,
    initiateStorageUpload,
    presignStoragePart,
    uploadDirectStorageFile,
    uploadPresignedStoragePart,
    uploadStoragePart,
} from "@/modules/_workspace/_console/composables/_storage";

const API_ROOT = "/apis/api.pudans.tolim.cn/v1alpha1";

type WorkspaceAssetSource = "chat" | "creation" | "upload";

export type WorkspaceAssetUploadOptions = {
    module: string;
    source: WorkspaceAssetSource;
};

export async function uploadWorkspaceAssets(
    files: readonly File[],
    options: WorkspaceAssetUploadOptions,
): Promise<string[]> {
    const assetIds: string[] = [];

    for (const file of files) {
        const storageFile = await uploadWorkspaceFile(file, options.module);
        assetIds.push(await registerWorkspaceAsset(file, storageFile, options.source));
    }

    return assetIds;
}

async function uploadWorkspaceFile(file: File, module: string): Promise<StorageFile> {
    if (file.size < 1) {
        throw new Error(`文件「${file.name}」是空文件。`);
    }

    const ticket = await initiateStorageUpload({
        contentType: file.type || undefined,
        module,
        originalName: file.name,
        policy: "",
        size: file.size,
    });

    try {
        if (ticket.directUpload) {
            await uploadDirectStorageFile(ticket.directUpload, file).promise;
            return await completeStorageUpload(ticket.fileId, ticket.uploadToken, []);
        }

        if (!ticket.uploadToken || ticket.partCount < 1 || (ticket.partCount > 1 && ticket.chunkSize <= 0)) {
            throw new Error("上传会话参数无效。");
        }

        for (let part = 1; part <= ticket.partCount; part += 1) {
            const chunkSize = ticket.chunkSize || file.size;
            const offset = (part - 1) * chunkSize;
            const body = file.slice(offset, offset + chunkSize);

            if (ticket.presigned) {
                const partUrl = await presignStoragePart(ticket.fileId, ticket.uploadToken, part);

                await uploadPresignedStoragePart(partUrl.url, body).promise;
            } else {
                await uploadStoragePart(ticket.fileId, ticket.uploadToken, part, body).promise;
            }
        }

        return await completeStorageUpload(ticket.fileId, ticket.uploadToken, []);
    } catch (error) {
        await abortStorageUpload(ticket.fileId, ticket.uploadToken).catch(() => undefined);
        throw error;
    }
}

async function registerWorkspaceAsset(
    file: File,
    storageFile: StorageFile,
    source: WorkspaceAssetSource,
): Promise<string> {
    const response = await axiosInstance.post<Asset>(
        `${API_ROOT}/assets/-/register`,
        {
            contentType: file.type || storageFile.spec.contentType,
            kind: assetKind(file),
            name: file.name,
            size: file.size,
            source,
            storageFileId: storageFile.metadata.name,
        },
        { headers: csrfHeaders() },
    );

    if (!response.data.id) {
        throw new Error(`文件「${file.name}」登记失败。`);
    }

    return response.data.id;
}

function assetKind(file: File): "audio" | "document" | "image" | "other" | "video" {
    if (file.type.startsWith("image/")) {
        return "image";
    }
    if (file.type.startsWith("video/")) {
        return "video";
    }
    if (file.type.startsWith("audio/")) {
        return "audio";
    }
    if (
        file.type.startsWith("text/") ||
        file.type === "application/pdf" ||
        file.name.endsWith(".doc") ||
        file.name.endsWith(".docx")
    ) {
        return "document";
    }

    return "other";
}

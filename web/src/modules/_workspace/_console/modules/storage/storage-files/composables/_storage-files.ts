import { type StorageFile, createStorageFileDownloadUrl } from "../../../../composables/_storage";

export type StorageFilesTab = "active" | "trashed";
export type StorageFilesViewMode = "gallery" | "list";

export const STORAGE_FILES_VIEW_MODE_KEY = "console.storage-files.view-mode";
const STORAGE_FILES_QUERY_ROOT = ["console", "storage-files"] as const;
export const STORAGE_FILES_LIST_QUERY_ROOT = [...STORAGE_FILES_QUERY_ROOT, "list"] as const;
export const STORAGE_FILES_TOTAL_QUERY_ROOT = [...STORAGE_FILES_QUERY_ROOT, "total"] as const;
export const STORAGE_FILES_DOWNLOAD_URL_QUERY_ROOT = [...STORAGE_FILES_QUERY_ROOT, "download-url"] as const;
export const STORAGE_FILE_PERMISSIONS = {
    delete: "storage-files:delete",
    download: "storage-files:download",
    purge: "storage-files:purge",
    restore: "storage-files:restore",
    view: "storage-files:view",
} as const;

const DOWNLOAD_URL_STALE_TIME_MS = 4 * 60_000;
const IMAGE_EXTENSIONS = new Set([
    "apng",
    "avif",
    "bmp",
    "gif",
    "heic",
    "heif",
    "ico",
    "jfif",
    "jpeg",
    "jpg",
    "png",
    "svg",
    "tif",
    "tiff",
    "webp",
]);

export function storageFileName(file: StorageFile): string {
    return file.spec.originalName?.trim() || file.metadata.name;
}

export function storageFileExtension(file: StorageFile): string {
    const explicitExtension = file.spec.extension?.trim();
    const originalName = file.spec.originalName?.trim() ?? "";
    const extensionStart = originalName.lastIndexOf(".");
    const fallbackExtension =
        extensionStart > 0 && extensionStart < originalName.length - 1 ? originalName.slice(extensionStart + 1) : "";
    const extension = explicitExtension || fallbackExtension || "";

    return extension.replace(/^\./, "").toLowerCase();
}

export function isImageStorageFile(file: StorageFile): boolean {
    const contentType = file.spec.contentType?.trim().toLowerCase();

    return Boolean(contentType?.startsWith("image/")) || IMAGE_EXTENSIONS.has(storageFileExtension(file));
}

export function storageFileDownloadUrlQueryOptions(name: string) {
    return {
        queryFn: async () => createStorageFileDownloadUrl(name),
        queryKey: [...STORAGE_FILES_DOWNLOAD_URL_QUERY_ROOT, name] as const,
        staleTime: DOWNLOAD_URL_STALE_TIME_MS,
    };
}

import { formatFileSize } from "@/modules/workspace/console/storage-policies/lib/model";

import type { StorageFileItem, StorageFileState } from "./api";

export const STORAGE_FILE_STATE_OPTIONS: { value: StorageFileState; label: string }[] = [
  { value: "ACTIVE", label: "正常" },
  { value: "TRASHED", label: "回收站" },
  { value: "UPLOADING", label: "上传中" },
];

const STORAGE_FILE_STATE_LABELS: Record<StorageFileState, string> = {
  ACTIVE: "正常",
  TRASHED: "回收站",
  UPLOADING: "上传中",
};

export function storageFileStateLabel(state: StorageFileState | undefined): string {
  return state ? STORAGE_FILE_STATE_LABELS[state] : "未知";
}

export function storageFileName(file: StorageFileItem): string {
  return file.spec.originalName || file.metadata.name;
}

export function formatStorageFileSize(bytes: number | undefined): string {
  return bytes && bytes > 0 ? formatFileSize(bytes) : "0 B";
}

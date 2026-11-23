"use client";

import { GlobalDialog } from "@/components/ui";

import { ObjectDetails } from "../../../../components/object-details";
import type { StorageFile } from "../../../../composables/_storage";
import { isImageStorageFile, storageFileName } from "../composables/_storage-files";
import { StorageFilePreview } from "./storage-file-preview";

type StorageFileDetailsModalProps = {
    canPreview: boolean;
    file?: StorageFile;
    onClose: () => void;
    open: boolean;
};

export function StorageFileDetailsModal({ canPreview, file, onClose, open }: StorageFileDetailsModalProps) {
    const showPreview = Boolean(canPreview && file && file.spec.state === "ACTIVE" && isImageStorageFile(file));

    return (
        <GlobalDialog
            footer={false}
            onClose={onClose}
            open={open}
            title={file ? storageFileName(file) : "文件详情"}
            width={760}
        >
            {file ? (
                <div className="flex min-w-0 flex-col gap-4">
                    {showPreview ? (
                        <StorageFilePreview canPreview={canPreview} className="h-64 w-full rounded-2xl" file={file} />
                    ) : null}
                    <ObjectDetails value={file} />
                </div>
            ) : null}
        </GlobalDialog>
    );
}

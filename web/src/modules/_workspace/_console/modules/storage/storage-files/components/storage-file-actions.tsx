"use client";

import { BrowseIcon, DeleteIcon, DownloadIcon, MoreIcon, RollbackIcon } from "tdesign-icons-react";
import { Button, Dropdown } from "tdesign-react";
import type { DropdownOption } from "tdesign-react";

import type { StorageFile } from "../../../../composables/_storage";
import { storageFileName } from "../composables/_storage-files";

type StorageFileActionsProps = {
    canDelete: boolean;
    canDownload: boolean;
    canPurge: boolean;
    canRestore: boolean;
    disabled?: boolean;
    file: StorageFile;
    loading?: boolean;
    onDelete: (file: StorageFile) => void;
    onDetails: (file: StorageFile) => void;
    onDownload: (file: StorageFile) => void;
    onPurge: (file: StorageFile) => void;
    onRestore: (file: StorageFile) => void;
};

export function StorageFileActions({
    canDelete,
    canDownload,
    canPurge,
    canRestore,
    disabled,
    file,
    loading,
    onDelete,
    onDetails,
    onDownload,
    onPurge,
    onRestore,
}: StorageFileActionsProps) {
    const displayName = storageFileName(file);
    const isTrashed = file.spec.state === "TRASHED";
    const actions: DropdownOption[] = [
        {
            content: "详情",
            prefixIcon: <BrowseIcon size={16} />,
            value: "details",
        },
    ];

    if (isTrashed) {
        if (canRestore) {
            actions.push({
                content: "恢复",
                prefixIcon: <RollbackIcon size={16} />,
                value: "restore",
            });
        }
        if (canPurge) {
            actions.push(
                { divider: true, value: "purge-divider" },
                {
                    content: "彻底删除",
                    prefixIcon: <DeleteIcon size={16} />,
                    theme: "error",
                    value: "purge",
                },
            );
        }
    } else {
        if (canDownload) {
            actions.push({
                content: "下载",
                prefixIcon: <DownloadIcon size={16} />,
                value: "download",
            });
        }
        if (canDelete) {
            actions.push(
                { divider: true, value: "trash-divider" },
                {
                    content: "移入回收站",
                    prefixIcon: <DeleteIcon size={16} />,
                    theme: "error",
                    value: "trash",
                },
            );
        }
    }

    const handleAction = (item: DropdownOption) => {
        if (item.value === "details") {
            onDetails(file);
        } else if (item.value === "restore") {
            onRestore(file);
        } else if (item.value === "purge") {
            onPurge(file);
        } else if (item.value === "download") {
            onDownload(file);
        } else if (item.value === "trash") {
            onDelete(file);
        }
    };

    return (
        <Dropdown
            disabled={disabled || loading}
            onClick={handleAction}
            options={actions}
            placement="bottom-right"
            trigger="click"
        >
            <Button
                aria-label={`${displayName} 操作`}
                disabled={disabled}
                icon={<MoreIcon />}
                loading={loading}
                shape="square"
                size="small"
                variant="text"
            />
        </Dropdown>
    );
}

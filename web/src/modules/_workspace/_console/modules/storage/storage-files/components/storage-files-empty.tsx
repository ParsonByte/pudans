"use client";

import { DeleteIcon, FolderOpenIcon } from "tdesign-icons-react";
import { Empty } from "tdesign-react";

import type { StorageFilesTab } from "../composables/_storage-files";

type StorageFilesEmptyProps = {
    tab: StorageFilesTab;
};

export function StorageFilesEmpty({ tab }: StorageFilesEmptyProps) {
    return (
        <Empty
            className="py-8"
            image={tab === "trashed" ? <DeleteIcon size={48} /> : <FolderOpenIcon size={48} />}
            title={tab === "trashed" ? "回收站为空" : "暂无文件"}
        />
    );
}

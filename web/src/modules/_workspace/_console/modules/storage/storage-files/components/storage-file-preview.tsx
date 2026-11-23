"use client";

import { useState } from "react";

import { useQuery } from "@tanstack/react-query";
import {
    FileCodeIcon,
    FileExcelIcon,
    FileIcon,
    FileImageIcon,
    FilePdfIcon,
    FilePowerpointIcon,
    FileTxtIcon,
    FileWordIcon,
    FileZipIcon,
    MusicIcon,
    VideoIcon,
} from "tdesign-icons-react";
import { Image, ImageViewer, Loading } from "tdesign-react";

import { useDesignTokens } from "@/components/ui";

import type { StorageFile } from "../../../../composables/_storage";
import {
    isImageStorageFile,
    storageFileDownloadUrlQueryOptions,
    storageFileExtension,
    storageFileName,
} from "../composables/_storage-files";

type StorageFilePreviewProps = {
    canPreview: boolean;
    className?: string;
    file: StorageFile;
};

const ARCHIVE_EXTENSIONS = new Set(["7z", "bz2", "gz", "rar", "tar", "xz", "zip"]);
const AUDIO_EXTENSIONS = new Set(["aac", "flac", "m4a", "mp3", "ogg", "wav"]);
const CODE_EXTENSIONS = new Set(["css", "html", "js", "json", "jsx", "md", "ts", "tsx", "xml", "yaml", "yml"]);
const SPREADSHEET_EXTENSIONS = new Set(["csv", "xls", "xlsx"]);
const VIDEO_EXTENSIONS = new Set(["avi", "m4v", "mkv", "mov", "mp4", "webm"]);
const FILE_TYPE_ICON_SIZE = 30;

export function StorageFilePreview({ canPreview, className, file }: StorageFilePreviewProps) {
    const token = useDesignTokens();
    const [failedUrl, setFailedUrl] = useState<string>();
    const activeImage = canPreview && isImageStorageFile(file) && file.spec.state === "ACTIVE";
    const imageQuery = useQuery({
        ...storageFileDownloadUrlQueryOptions(file.metadata.name),
        enabled: activeImage,
    });
    const imageUrl = imageQuery.data?.trim();
    const showImage = activeImage && Boolean(imageUrl) && failedUrl !== imageUrl;

    return (
        <div
            className={`relative flex items-center justify-center overflow-hidden ${className ?? ""}`}
            style={{ background: token.colorFillQuaternary }}
        >
            {showImage && imageUrl ? (
                <ImageViewer
                    images={[imageUrl]}
                    title={storageFileName(file)}
                    trigger={({ open }) => (
                        <Image
                            alt={storageFileName(file)}
                            className="h-full w-full cursor-zoom-in"
                            fit="cover"
                            onClick={() => open()}
                            onError={() => setFailedUrl(imageUrl)}
                            src={imageUrl}
                        />
                    )}
                />
            ) : imageQuery.isPending && activeImage ? (
                <Loading size="small" />
            ) : (
                <FileTypePlaceholder file={file} />
            )}
        </div>
    );
}

function FileTypePlaceholder({ file }: { file: StorageFile }) {
    const token = useDesignTokens();
    const extension = storageFileExtension(file);
    const color = isImageStorageFile(file)
        ? token.colorPrimary
        : extension === "pdf"
          ? token.colorError
          : SPREADSHEET_EXTENSIONS.has(extension) || isDocumentExtension(extension)
            ? token.colorSuccess
            : VIDEO_EXTENSIONS.has(extension) || AUDIO_EXTENSIONS.has(extension)
              ? token.colorInfo
              : ARCHIVE_EXTENSIONS.has(extension)
                ? token.colorWarning
                : token.colorTextTertiary;

    return (
        <span
            className="flex size-14 items-center justify-center rounded-2xl"
            style={{ background: token.colorBgContainer, color, boxShadow: token.boxShadowTertiary }}
        >
            {fileTypeGlyph(file, FILE_TYPE_ICON_SIZE)}
        </span>
    );
}

function isDocumentExtension(extension: string): boolean {
    return ["doc", "docx", "pdf", "ppt", "pptx", "rtf", "txt"].includes(extension);
}

function fileTypeGlyph(file: StorageFile, size: number) {
    const extension = storageFileExtension(file);

    if (ARCHIVE_EXTENSIONS.has(extension)) {
        return <FileZipIcon size={size} />;
    }
    if (AUDIO_EXTENSIONS.has(extension)) {
        return <MusicIcon size={size} />;
    }
    if (CODE_EXTENSIONS.has(extension)) {
        return <FileCodeIcon size={size} />;
    }
    if (SPREADSHEET_EXTENSIONS.has(extension)) {
        return <FileExcelIcon size={size} />;
    }
    if (extension === "pdf") {
        return <FilePdfIcon size={size} />;
    }
    if (["doc", "docx", "rtf"].includes(extension)) {
        return <FileWordIcon size={size} />;
    }
    if (["ppt", "pptx"].includes(extension)) {
        return <FilePowerpointIcon size={size} />;
    }
    if (["txt", "md"].includes(extension)) {
        return <FileTxtIcon size={size} />;
    }
    if (VIDEO_EXTENSIONS.has(extension)) {
        return <VideoIcon size={size} />;
    }

    return isImageStorageFile(file) ? <FileImageIcon size={size} /> : <FileIcon size={size} />;
}

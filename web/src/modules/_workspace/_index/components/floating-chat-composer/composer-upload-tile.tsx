import { useEffect, useMemo } from "react";

import { ImageAddIcon, VideoIcon } from "tdesign-icons-react";
import { Button, Tooltip } from "tdesign-react";

import styles from "../../styles/floating-chat-composer.module.css";

type ComposerUploadTileProps = {
    file: File | null;
    label: string;
    mediaType: "image" | "video";
    onSelect: () => void;
};

function usePreviewUrl(file: File | null): string | undefined {
    const previewUrl = useMemo(() => (file ? URL.createObjectURL(file) : undefined), [file]);

    useEffect(
        () => () => {
            if (previewUrl) {
                URL.revokeObjectURL(previewUrl);
            }
        },
        [previewUrl],
    );

    return previewUrl;
}

export function ComposerUploadTile({ file, label, mediaType, onSelect }: ComposerUploadTileProps) {
    const previewUrl = usePreviewUrl(file);
    const action = file ? `替换${label}` : `上传${label}`;

    return (
        <Tooltip content={file ? `${action}：${file.name}` : action}>
            <Button
                aria-label={action}
                className={`${styles.uploadButton} ${styles.mediaUploadButton}`}
                onClick={onSelect}
                shape="square"
                type="button"
                variant="text"
            >
                {previewUrl ? (
                    mediaType === "video" ? (
                        <video
                            aria-hidden
                            autoPlay
                            className={styles.mediaUploadVideo}
                            loop
                            muted
                            playsInline
                            src={previewUrl}
                        />
                    ) : (
                        <span
                            aria-hidden
                            className={styles.mediaUploadImage}
                            style={{ backgroundImage: `url("${previewUrl}")` }}
                        />
                    )
                ) : mediaType === "video" ? (
                    <VideoIcon size={24} />
                ) : (
                    <ImageAddIcon size={24} />
                )}
            </Button>
        </Tooltip>
    );
}

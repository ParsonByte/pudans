import { AddIcon, ImageAddIcon, VideoIcon } from "tdesign-icons-react";
import { Button, Tooltip } from "tdesign-react";

import styles from "../../styles/floating-chat-composer.module.css";

type ComposerUploadProps = {
    imageCount: number;
    onSelectImage: () => void;
    onSelectReference: () => void;
    onSelectVideo: () => void;
    referenceCount: number;
    showReference: boolean;
    showVideo: boolean;
    videoCount: number;
};

export function ComposerUpload({
    imageCount,
    onSelectImage,
    onSelectReference,
    onSelectVideo,
    referenceCount,
    showReference,
    showVideo,
    videoCount,
}: ComposerUploadProps) {
    if (showReference) {
        return (
            <div className={styles.uploadGroup}>
                <Tooltip content="上传参考图片或视频">
                    <Button
                        aria-label="上传参考图片或视频"
                        className={styles.uploadButton}
                        onClick={onSelectReference}
                        shape="square"
                        type="button"
                        variant="text"
                    >
                        <span className={styles.uploadIcon}>
                            <AddIcon size={24} />
                            {referenceCount > 0 ? <span className={styles.uploadCount}>{referenceCount}</span> : null}
                        </span>
                    </Button>
                </Tooltip>
            </div>
        );
    }

    return (
        <div className={styles.uploadGroup}>
            <Tooltip content="上传参考图片">
                <Button
                    aria-label="上传参考图片"
                    className={styles.uploadButton}
                    onClick={onSelectImage}
                    shape="square"
                    type="button"
                    variant="text"
                >
                    <span className={styles.uploadIcon}>
                        <ImageAddIcon size={24} />
                        {imageCount > 0 ? <span className={styles.uploadCount}>{imageCount}</span> : null}
                    </span>
                </Button>
            </Tooltip>
            {showVideo ? (
                <Tooltip content="上传参考视频">
                    <Button
                        aria-label="上传参考视频"
                        className={styles.uploadButton}
                        onClick={onSelectVideo}
                        shape="square"
                        type="button"
                        variant="text"
                    >
                        <span className={styles.uploadIcon}>
                            <VideoIcon size={24} />
                            {videoCount > 0 ? <span className={styles.uploadCount}>{videoCount}</span> : null}
                        </span>
                    </Button>
                </Tooltip>
            ) : null}
        </div>
    );
}

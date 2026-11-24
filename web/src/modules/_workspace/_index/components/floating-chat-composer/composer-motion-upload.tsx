import { ChevronRightIcon } from "tdesign-icons-react";

import { ComposerUploadTile } from "./composer-upload-tile";
import styles from "./floating-chat-composer.module.css";

type ComposerMotionUploadProps = {
    image: File | null;
    onSelectImage: () => void;
    onSelectVideo: () => void;
    video: File | null;
};

export function ComposerMotionUpload({ image, onSelectImage, onSelectVideo, video }: ComposerMotionUploadProps) {
    return (
        <div className={`${styles.uploadGroup} ${styles.mediaPairGroup}`}>
            <ComposerUploadTile file={image} label="角色图片" mediaType="image" onSelect={onSelectImage} />
            <span aria-hidden className={styles.mediaPairArrow}>
                <ChevronRightIcon size={14} />
            </span>
            <ComposerUploadTile file={video} label="动作视频" mediaType="video" onSelect={onSelectVideo} />
        </div>
    );
}

import { ChevronRightIcon } from "tdesign-icons-react";

import { ComposerUploadTile } from "./composer-upload-tile";
import styles from "./floating-chat-composer.module.css";

type ComposerFrameUploadProps = {
    firstFrame: File | null;
    lastFrame: File | null;
    onSelectFirst: () => void;
    onSelectLast: () => void;
};

export function ComposerFrameUpload({ firstFrame, lastFrame, onSelectFirst, onSelectLast }: ComposerFrameUploadProps) {
    return (
        <div className={`${styles.uploadGroup} ${styles.mediaPairGroup}`}>
            <ComposerUploadTile file={firstFrame} label="首帧" mediaType="image" onSelect={onSelectFirst} />
            <span aria-hidden className={styles.mediaPairArrow}>
                <ChevronRightIcon size={14} />
            </span>
            <ComposerUploadTile file={lastFrame} label="尾帧" mediaType="image" onSelect={onSelectLast} />
        </div>
    );
}

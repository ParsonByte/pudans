import styles from "../../styles/floating-chat-composer.module.css";
import { ComposerUploadTile } from "./composer-upload-tile";

type ComposerSingleVideoUploadProps = {
    file: File | null;
    onSelect: () => void;
};

export function ComposerSingleVideoUpload({ file, onSelect }: ComposerSingleVideoUploadProps) {
    return (
        <div className={styles.uploadGroup}>
            <ComposerUploadTile file={file} label="视频" mediaType="video" onSelect={onSelect} />
        </div>
    );
}

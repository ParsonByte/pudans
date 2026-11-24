import { AddIcon, AudioIcon, ImageAddIcon, VideoIcon } from "tdesign-icons-react";

import type { ComposerMediaMode, ComposerToolMode } from "./composer-types";
import styles from "./floating-chat-composer.module.css";

type ComposerCollapsedBarProps = {
    characterCount: number;
    maxLength: number;
    mediaMode: ComposerMediaMode;
    onExpand: () => void;
    placeholder: string;
    toolMode: ComposerToolMode;
    value: string;
};

function getCollapsedIcons(mediaMode: ComposerMediaMode, toolMode: ComposerToolMode) {
    if (mediaMode === "audio") {
        return [{ key: "audio", Icon: AudioIcon }];
    }

    if (mediaMode === "image") {
        return [{ key: "image", Icon: ImageAddIcon }];
    }

    if (toolMode === "motion") {
        return [
            { key: "image", Icon: ImageAddIcon },
            { key: "video", Icon: VideoIcon },
        ];
    }

    if (toolMode === "frame") {
        return [
            { key: "first", Icon: ImageAddIcon },
            { key: "last", Icon: ImageAddIcon },
        ];
    }

    if (toolMode === "reference") {
        return [{ key: "reference", Icon: AddIcon }];
    }

    if (toolMode === "edit" || toolMode === "continue") {
        return [{ key: "video", Icon: VideoIcon }];
    }

    return [{ key: "image", Icon: ImageAddIcon }];
}

export function ComposerCollapsedBar({
    characterCount,
    maxLength,
    mediaMode,
    onExpand,
    placeholder,
    toolMode,
    value,
}: ComposerCollapsedBarProps) {
    const icons = getCollapsedIcons(mediaMode, toolMode);

    return (
        <button aria-label="展开对话输入框" className={styles.collapsedBar} onClick={onExpand} type="button">
            <span
                aria-hidden
                className={`${styles.collapsedIcons} ${icons.length > 1 ? styles.collapsedIconsMultiple : ""}`}
            >
                {icons.map(({ key, Icon }) => (
                    <span className={styles.collapsedIcon} key={key}>
                        <Icon size={24} />
                    </span>
                ))}
            </span>
            <span className={styles.collapsedBody}>
                <span className={`${styles.collapsedText} ${value ? "" : styles.collapsedPlaceholder}`}>
                    {value || placeholder}
                </span>
                <span className={styles.collapsedCounter}>
                    {characterCount}/{maxLength}
                </span>
            </span>
        </button>
    );
}

import {
    AudioIcon,
    ChevronDownIcon,
    EditIcon,
    Image1Icon,
    ImageAddIcon,
    ImageSearchIcon,
    InfoCircleIcon,
    ModeTextIcon,
    TextformatLongerIcon,
    UserIcon,
} from "tdesign-icons-react";
import { Button, Tooltip } from "tdesign-react";

import { ComposerModeSwitch } from "./composer-mode-switch";
import { ComposerResetButton } from "./composer-reset-button";
import { type ComposerMediaMode, type ComposerToolMode, getComposerToolModes } from "./composer-types";
import styles from "./floating-chat-composer.module.css";

const TOOL_MODE_META = {
    continue: { icon: <TextformatLongerIcon size={16} />, label: "续写" },
    default: { icon: <ImageAddIcon size={16} />, label: "默认" },
    edit: { icon: <EditIcon size={16} />, label: "编辑" },
    frame: { icon: <Image1Icon size={16} />, label: "首尾帧" },
    motion: { icon: <UserIcon size={16} />, label: "动作模仿" },
    reference: { icon: <ImageSearchIcon size={16} />, label: "参考" },
    text: { icon: <ModeTextIcon size={16} />, label: "图文" },
} satisfies Record<ComposerToolMode, { icon: React.ReactNode; label: string }>;

type ComposerModeBarProps = {
    activeMode: ComposerToolMode;
    mediaMode: ComposerMediaMode;
    onChangeMediaMode: (mode: ComposerMediaMode) => void;
    onChangeMode: (mode: ComposerToolMode) => void;
    onCollapse: () => void;
    onReset: () => void;
};

export function ComposerModeBar({
    activeMode,
    mediaMode,
    onChangeMediaMode,
    onChangeMode,
    onCollapse,
    onReset,
}: ComposerModeBarProps) {
    const toolModes = getComposerToolModes(mediaMode);

    return (
        <div className={styles.modeBar}>
            <div aria-label="创作模式" className={styles.modeActions} role="tablist">
                {toolModes.map((mode) => (
                    <Button
                        aria-selected={activeMode === mode}
                        className={`${styles.modeButton} ${activeMode === mode ? styles.modeButtonActive : ""}`}
                        key={mode}
                        onClick={() => onChangeMode(mode)}
                        role="tab"
                        size="small"
                        type="button"
                        variant="text"
                    >
                        {mode === "default" && mediaMode === "audio" ? (
                            <AudioIcon size={16} />
                        ) : (
                            TOOL_MODE_META[mode].icon
                        )}
                        <span>{TOOL_MODE_META[mode].label}</span>
                    </Button>
                ))}
            </div>
            <div className={styles.modeBarEnd}>
                <ComposerModeSwitch onChange={onChangeMediaMode} value={mediaMode} />
                <span className={styles.aiHint}>
                    <InfoCircleIcon size={14} />
                    内容由 AI 生成
                </span>
                <ComposerResetButton onReset={onReset} />
                <Tooltip content="收起面板">
                    <Button
                        aria-label="收起面板"
                        className={styles.iconButton}
                        icon={<ChevronDownIcon />}
                        onClick={onCollapse}
                        shape="square"
                        type="button"
                        variant="text"
                    />
                </Tooltip>
            </div>
        </div>
    );
}

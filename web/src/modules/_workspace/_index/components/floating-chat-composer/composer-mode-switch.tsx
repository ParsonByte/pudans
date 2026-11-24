import { useState } from "react";

import { AiImageIcon, AiVideoIcon, AudioIcon, CheckIcon, ChevronDownIcon } from "tdesign-icons-react";
import { Button, Popup } from "tdesign-react";

import styles from "../../styles/floating-chat-composer.module.css";
import type { ComposerMediaMode } from "./composer-types";

const MEDIA_MODE_OPTIONS = [
    {
        description: "用文字或图片生成视频",
        icon: <AiVideoIcon size={20} />,
        label: "视频",
        value: "video",
    },
    {
        description: "生成静态图片内容",
        icon: <AiImageIcon size={20} />,
        label: "图片",
        value: "image",
    },
    {
        description: "将文字转换为自然语音",
        icon: <AudioIcon size={20} />,
        label: "音频",
        value: "audio",
    },
] as const;

type ComposerModeSwitchProps = {
    onChange: (mode: ComposerMediaMode) => void;
    value: ComposerMediaMode;
};

export function ComposerModeSwitch({ onChange, value }: ComposerModeSwitchProps) {
    const [visible, setVisible] = useState(false);
    const currentOption = MEDIA_MODE_OPTIONS.find((option) => option.value === value) ?? MEDIA_MODE_OPTIONS[0];

    const selectMode = (mode: ComposerMediaMode) => {
        onChange(mode);
        setVisible(false);
    };

    return (
        <Popup
            content={
                <div className={`${styles.theme} ${styles.modelPanel} ${styles.modePanel}`}>
                    <span className={styles.modelPanelTitle}>生成类型</span>
                    {MEDIA_MODE_OPTIONS.map((option) => {
                        const active = option.value === value;

                        return (
                            <Button
                                aria-pressed={active}
                                className={`${styles.modelOption} ${active ? styles.modelOptionActive : ""}`}
                                key={option.value}
                                onClick={() => selectMode(option.value)}
                                type="button"
                                variant="text"
                            >
                                <span className={styles.modelOptionIcon}>{option.icon}</span>
                                <span className={styles.modelOptionText}>
                                    <span className={styles.modelOptionName}>{option.label}</span>
                                    <span className={styles.modelOptionMeta}>{option.description}</span>
                                </span>
                                {active ? <CheckIcon /> : null}
                            </Button>
                        );
                    })}
                </div>
            }
            onVisibleChange={setVisible}
            overlayClassName={styles.modePopup}
            placement="top-right"
            trigger="click"
            visible={visible}
        >
            <Button
                aria-label={`切换生成模式，当前${currentOption.label}`}
                className={styles.modeTrigger}
                icon={currentOption.icon}
                type="button"
                variant="text"
            >
                {currentOption.label}
                <ChevronDownIcon className={styles.modeTriggerArrow} size={12} />
            </Button>
        </Popup>
    );
}

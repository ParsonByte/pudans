"use client";

import { useState } from "react";

import { Check, ChevronRight } from "lucide-react";
import { Button, Popup } from "tdesign-react";

import styles from "../../styles/ai-chat-composer.module.css";
import type { AiChatComposerMode } from "./chat-input-types";

const MODE_OPTIONS: ReadonlyArray<{ label: string; value: AiChatComposerMode }> = [
    { label: "快速", value: "fast" },
    { label: "专家", value: "expert" },
];

type ChatInputModeMenuProps = {
    onChange: (mode: AiChatComposerMode) => void;
    value: AiChatComposerMode;
};

export function ChatInputModeMenu({ onChange, value }: ChatInputModeMenuProps) {
    const [visible, setVisible] = useState(false);
    const activeLabel = MODE_OPTIONS.find((option) => option.value === value)?.label ?? "快速";

    return (
        <Popup
            content={
                <div className={`${styles.panel} ${styles.modeMenu}`}>
                    {MODE_OPTIONS.map((option) => (
                        <Button
                            className={`${styles.modeOption} ${option.value === value ? styles.modeOptionActive : ""}`}
                            key={option.value}
                            onClick={() => {
                                onChange(option.value);
                                setVisible(false);
                            }}
                            type="button"
                            variant="text"
                        >
                            {option.label}
                            {option.value === value ? <Check size={16} strokeWidth={1.8} /> : null}
                        </Button>
                    ))}
                </div>
            }
            onVisibleChange={setVisible}
            overlayClassName={styles.panelPopup}
            placement="top-right"
            trigger="click"
            visible={visible}
        >
            <Button aria-label={`回答模式：${activeLabel}`} className={styles.modeButton} type="button" variant="text">
                {activeLabel}
                <ChevronRight className={styles.modeArrow} size={14} strokeWidth={1.8} />
            </Button>
        </Popup>
    );
}

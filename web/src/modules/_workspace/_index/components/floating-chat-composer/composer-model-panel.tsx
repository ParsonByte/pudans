import { AiImageIcon, AiVideoIcon, AudioIcon, CheckIcon } from "tdesign-icons-react";
import { Button } from "tdesign-react";

import type { ComposerMediaMode } from "./composer-types";
import styles from "./floating-chat-composer.module.css";

const COMPOSER_MODEL_OPTIONS = [{ label: "DANS", value: "dans" }] as const;

export type ComposerModelPanelProps = {
    mediaMode: ComposerMediaMode;
    onChange: (model: "dans") => void;
    value: "dans";
};

export function ComposerModelPanel({ mediaMode, onChange, value }: ComposerModelPanelProps) {
    return (
        <div className={`${styles.theme} ${styles.modelPanel}`}>
            <span className={styles.modelPanelTitle}>模型</span>
            {COMPOSER_MODEL_OPTIONS.map((model) => {
                const active = model.value === value;

                return (
                    <Button
                        aria-pressed={active}
                        className={`${styles.modelOption} ${active ? styles.modelOptionActive : ""}`}
                        key={model.value}
                        onClick={() => onChange(model.value)}
                        type="button"
                        variant="text"
                    >
                        <span className={styles.modelOptionIcon}>
                            {mediaMode === "video" ? (
                                <AiVideoIcon size={20} />
                            ) : mediaMode === "audio" ? (
                                <AudioIcon size={20} />
                            ) : (
                                <AiImageIcon size={20} />
                            )}
                        </span>
                        <span className={styles.modelOptionText}>
                            <span className={styles.modelOptionName}>{model.label}</span>
                            <span className={styles.modelOptionMeta}>
                                {mediaMode === "video" ? "视频生成" : mediaMode === "audio" ? "语音生成" : "图片生成"}
                            </span>
                        </span>
                        {active ? <CheckIcon /> : null}
                    </Button>
                );
            })}
        </div>
    );
}

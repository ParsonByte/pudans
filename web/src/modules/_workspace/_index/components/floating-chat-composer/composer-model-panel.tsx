import { AiImageIcon, AiVideoIcon, AudioIcon, CheckIcon } from "tdesign-icons-react";
import { Button } from "tdesign-react";

import styles from "../../styles/floating-chat-composer.module.css";
import type { ComposerMediaMode, ComposerModelOption, ComposerModelValue } from "./composer-types";

export type ComposerModelPanelProps = {
    loading?: boolean;
    mediaMode: ComposerMediaMode;
    models: readonly ComposerModelOption[];
    onChange: (model: ComposerModelValue) => void;
    value: ComposerModelValue;
};

export function ComposerModelPanel({ loading = false, mediaMode, models, onChange, value }: ComposerModelPanelProps) {
    return (
        <div className={`${styles.theme} ${styles.modelPanel}`}>
            <span className={styles.modelPanelTitle}>模型</span>
            {models.length > 0 ? (
                models.map((model) => {
                    const active = model.id === value;

                    return (
                        <Button
                            aria-pressed={active}
                            className={`${styles.modelOption} ${active ? styles.modelOptionActive : ""}`}
                            key={model.id}
                            onClick={() => onChange(model.id)}
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
                                    {model.description ??
                                        (mediaMode === "video"
                                            ? "视频生成"
                                            : mediaMode === "audio"
                                              ? "语音生成"
                                              : "图片生成")}
                                </span>
                            </span>
                            {active ? <CheckIcon /> : null}
                        </Button>
                    );
                })
            ) : (
                <span className={styles.modelEmpty}>{loading ? "正在加载可用模型" : "当前工作区暂无可用模型"}</span>
            )}
        </div>
    );
}

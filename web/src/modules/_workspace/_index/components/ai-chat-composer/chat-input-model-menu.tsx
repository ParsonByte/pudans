"use client";

import { useState } from "react";

import { Check, ChevronRight, Diamond, Workflow } from "lucide-react";
import { Button, Popup } from "tdesign-react";

import styles from "../../styles/ai-chat-composer.module.css";

type ReasoningLevel = "high" | "low" | "medium";

type ModelOption = {
    badge?: string;
    id: string;
    label: string;
    price?: string;
};

const MODELS: readonly ModelOption[] = [
    { id: "auto", label: "自动", price: "小额免费" },
    { badge: "限时优惠", id: "dans-2.1-lite", label: "DANS 2.1 Lite", price: "0.01x" },
    { id: "dans-2.1-pro", label: "DANS 2.1 Pro", price: "0.65x" },
    { badge: "限时优惠", id: "deepseek-v4.1-flash", label: "DeepSeek V4.1 Flash", price: "0.01x" },
    { badge: "新上线", id: "glm-5.4", label: "GLM 5.4", price: "0.80x" },
    { badge: "新上线", id: "qwen-4-max", label: "Qwen 4 Max", price: "0.47x" },
    { id: "kimi-k3.1-pro", label: "Kimi K3.1 Pro", price: "0.95x" },
    { id: "kimi-k3.1-pro-fast", label: "Kimi K3.1 Pro 高速", price: "1.44x" },
    { id: "kimi-k3", label: "Kimi K3", price: "1.48x" },
    { id: "glm-5.3", label: "GLM 5.3", price: "0.80x" },
    { id: "glm-5.3-flash", label: "GLM 5.3 Flash", price: "0.08x" },
];

const REASONING_OPTIONS: ReadonlyArray<{ label: string; value: ReasoningLevel }> = [
    { label: "低", value: "low" },
    { label: "中", value: "medium" },
    { label: "高", value: "high" },
];

export function ChatInputModelMenu() {
    const [open, setOpen] = useState(false);
    const [reasoningOpen, setReasoningOpen] = useState(false);
    const [selectedModel, setSelectedModel] = useState("auto");
    const [reasoning, setReasoning] = useState<ReasoningLevel>("low");
    const activeModel = MODELS.find((model) => model.id === selectedModel) ?? MODELS[0];
    const activeReasoning = REASONING_OPTIONS.find((option) => option.value === reasoning) ?? REASONING_OPTIONS[0];

    return (
        <Popup
            content={
                <div className={styles.modelMenu}>
                    <div className={styles.modelList}>
                        {MODELS.map((model) => (
                            <Button
                                className={styles.modelOption}
                                key={model.id}
                                onClick={() => {
                                    setSelectedModel(model.id);
                                    setOpen(false);
                                }}
                                type="button"
                                variant="text"
                            >
                                <Diamond className={styles.modelIcon} size={16} strokeWidth={1.4} />
                                <span className={styles.modelName}>{model.label}</span>
                                {model.badge ? <span className={styles.modelBadge}>{model.badge}</span> : null}
                                {model.price ? (
                                    <span
                                        className={`${styles.modelPrice} ${
                                            model.id === "auto" ? styles.modelPriceAuto : ""
                                        }`}
                                    >
                                        {model.price}
                                    </span>
                                ) : null}
                                {model.id === selectedModel ? (
                                    <Check className={styles.modelCheck} size={18} strokeWidth={1.8} />
                                ) : null}
                            </Button>
                        ))}
                    </div>

                    <div className={styles.modelDivider} />

                    <div className={styles.reasoningWrap}>
                        <Button
                            className={styles.reasoningButton}
                            onClick={() => setReasoningOpen((current) => !current)}
                            type="button"
                            variant="text"
                        >
                            <Workflow size={16} strokeWidth={1.4} />
                            <span className={styles.reasoningLabel}>推理强度</span>
                            <span className={styles.reasoningValue}>{activeReasoning.label}</span>
                            <ChevronRight className={styles.reasoningArrow} size={15} strokeWidth={1.8} />
                        </Button>

                        {reasoningOpen ? (
                            <div className={styles.reasoningMenu}>
                                {REASONING_OPTIONS.map((option) => (
                                    <Button
                                        className={`${styles.reasoningOption} ${
                                            option.value === reasoning ? styles.reasoningOptionActive : ""
                                        }`}
                                        key={option.value}
                                        onClick={() => {
                                            setReasoning(option.value);
                                            setReasoningOpen(false);
                                        }}
                                        type="button"
                                        variant="text"
                                    >
                                        {option.label}
                                        {option.value === reasoning ? <Check size={17} strokeWidth={1.8} /> : null}
                                    </Button>
                                ))}
                            </div>
                        ) : null}
                    </div>
                </div>
            }
            onVisibleChange={(visible) => {
                setOpen(visible);

                if (!visible) {
                    setReasoningOpen(false);
                }
            }}
            overlayClassName={styles.modelPopup}
            placement="top-right"
            trigger="click"
            visible={open}
        >
            <Button
                aria-label={`模型：${activeModel.label}，推理强度：${activeReasoning.label}`}
                className={styles.modelButton}
                type="button"
                variant="text"
            >
                {activeModel.label}
                <span className={styles.modelButtonReasoning}>{activeReasoning.label}</span>
                <ChevronRight className={styles.modeArrow} size={14} strokeWidth={1.8} />
            </Button>
        </Popup>
    );
}

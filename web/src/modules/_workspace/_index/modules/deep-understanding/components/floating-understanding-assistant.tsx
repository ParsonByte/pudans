"use client";

import { useEffect, useRef, useState } from "react";

import { GraphicDesignTwo } from "@icon-park/react";
import { type TdChatSenderParams } from "@tdesign-react/chat";
import { CloseIcon, LightbulbIcon, LinkIcon, RefreshIcon } from "tdesign-icons-react";
import { MessagePlugin } from "tdesign-react";

import { useThemeSync } from "@/hooks/ui/use-theme-sync";

import { ChatSenderWithSlots } from "../../../components/chat-sender-with-slots";
import styles from "../styles/deep-understanding.module.css";

const DANS_ICON_COLORS: Record<"light" | "dark", [string, string]> = {
    dark: ["#9ca4ff", "#fff191"],
    light: ["#5f67e8", "#e0b634"],
};

const DIALOGUE_PROMPTS = [
    {
        icon: <LightbulbIcon size={14} />,
        prompt: "你目前理解了我什么？",
    },
    {
        icon: <LinkIcon size={14} />,
        prompt: "这条判断的依据是什么？",
    },
    {
        icon: <RefreshIcon size={14} />,
        prompt: "帮我修正一个过时的偏好",
    },
] as const;

export function FloatingUnderstandingAssistant() {
    const [open, setOpen] = useState(false);
    const [value, setValue] = useState("");
    const [submittedPrompt, setSubmittedPrompt] = useState("");
    const senderRef = useRef<HTMLElement>(null);
    const { isDark } = useThemeSync();
    const dansIconColors = DANS_ICON_COLORS[isDark ? "dark" : "light"];

    useEffect(() => {
        if (!open) {
            return;
        }

        const closeOnEscape = (event: KeyboardEvent) => {
            if (event.key === "Escape") {
                setOpen(false);
            }
        };

        document.addEventListener("keydown", closeOnEscape);

        return () => document.removeEventListener("keydown", closeOnEscape);
    }, [open]);

    const selectPrompt = (prompt: string) => {
        setValue(prompt);
        requestAnimationFrame(() => senderRef.current?.focus());
    };

    const submit = (rawPrompt: string) => {
        const prompt = rawPrompt.trim();

        if (!prompt) {
            return;
        }

        setSubmittedPrompt(prompt);
        setValue("");
        void MessagePlugin.info("认知中枢对话接口待接入");
    };

    return (
        <>
            {open ? (
                <section
                    aria-labelledby="understanding-assistant-title"
                    aria-modal="false"
                    className={styles.assistantPanel}
                    id="understanding-assistant-panel"
                    role="dialog"
                >
                    <header className={styles.assistantHeader}>
                        <span className={styles.assistantIdentity}>
                            <span className={styles.assistantAvatar}>
                                <GraphicDesignTwo fill={dansIconColors} size={19} strokeWidth={3.4} theme="two-tone" />
                            </span>
                            <span>
                                <strong id="understanding-assistant-title">认知中枢</strong>
                                <small>DANS · 仅当前工作区</small>
                            </span>
                        </span>
                        <span className={styles.assistantHeaderActions}>
                            <span className={styles.assistantStatus}>接口待接入</span>
                            <button
                                aria-label="收起认知中枢"
                                className={styles.assistantClose}
                                onClick={() => setOpen(false)}
                                type="button"
                            >
                                <CloseIcon size={16} />
                            </button>
                        </span>
                    </header>

                    <div aria-live="polite" className={styles.assistantBody}>
                        {submittedPrompt ? (
                            <div className={styles.dialogueExchange} key={submittedPrompt}>
                                <div className={styles.userPrompt}>{submittedPrompt}</div>
                                <div className={styles.pendingReply}>
                                    <span className={styles.pendingReplyIcon}>
                                        <GraphicDesignTwo
                                            fill={dansIconColors}
                                            size={15}
                                            strokeWidth={3.6}
                                            theme="two-tone"
                                        />
                                    </span>
                                    <span>
                                        <strong>等待理解服务响应</strong>
                                        <small>对话接口接入后，将从理解证据中生成回答。</small>
                                    </span>
                                </div>
                            </div>
                        ) : (
                            <div className={styles.assistantEmpty}>
                                <span className={styles.assistantEmptyIcon}>
                                    <GraphicDesignTwo
                                        fill={dansIconColors}
                                        size={27}
                                        strokeWidth={3.2}
                                        theme="two-tone"
                                    />
                                </span>
                                <strong>想知道我理解了你什么？</strong>
                                <p>询问判断依据、纠正过时认知，或告诉 DANS 接下来应该更关注什么。</p>
                                <div aria-label="对话示例" className={styles.promptList}>
                                    {DIALOGUE_PROMPTS.map((item) => (
                                        <button
                                            className={styles.promptButton}
                                            key={item.prompt}
                                            onClick={() => selectPrompt(item.prompt)}
                                            type="button"
                                        >
                                            <span aria-hidden className={styles.promptIcon}>
                                                {item.icon}
                                            </span>
                                            {item.prompt}
                                        </button>
                                    ))}
                                </div>
                            </div>
                        )}
                    </div>

                    <div className={styles.assistantComposer}>
                        <ChatSenderWithSlots
                            actions={["send"]}
                            autosize={{ maxRows: 4, minRows: 1 }}
                            className={styles.assistantSender}
                            onChange={(event) => setValue(event.detail)}
                            onSend={(event: CustomEvent<TdChatSenderParams>) => submit(event.detail.value)}
                            placeholder="问问 DANS 理解了什么，或直接纠正它..."
                            ref={senderRef}
                            value={value}
                        />
                    </div>
                </section>
            ) : null}

            <button
                aria-controls="understanding-assistant-panel"
                aria-expanded={open}
                aria-label={open ? "收起认知中枢" : "打开认知中枢"}
                className={`${styles.assistantLauncher} ${open ? styles.assistantLauncherOpen : ""}`}
                onClick={() => setOpen((current) => !current)}
                title="认知中枢"
                type="button"
            >
                <span aria-hidden className={styles.assistantPulse} />
                <GraphicDesignTwo fill={dansIconColors} size={30} strokeWidth={3.2} theme="two-tone" />
                <span aria-hidden className={styles.assistantOnline} />
            </button>
        </>
    );
}

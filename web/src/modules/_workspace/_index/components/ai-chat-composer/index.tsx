"use client";

import { type ChangeEvent, useRef, useState } from "react";

import { type TdChatSenderParams } from "@tdesign-react/chat";
import { MessagePlugin, Tag } from "tdesign-react";

import styles from "../../styles/ai-chat-composer.module.css";
import { ChatSenderWithSlots } from "../chat-sender-with-slots";
import { ChatInputAddPanel } from "./chat-input-add-panel";
import { ChatInputSkillPanel } from "./chat-input-skill-panel";
import { ChatInputToolbar } from "./chat-input-toolbar";
import {
    AI_CHAT_TOOL_PLACEHOLDERS,
    type AiChatComposerAddAction,
    type AiChatComposerMode,
    type AiChatComposerPanel,
    type AiChatComposerSkill,
    type AiChatComposerTool,
    type AiChatComposerVariant,
} from "./chat-input-types";

const ADD_ACTION_LABELS: Record<AiChatComposerAddAction, string> = {
    "cloud-computer": "云电脑",
    "create-material": "创作资料",
    library: "资料库文件选择",
    upload: "文件上传",
};

type AiChatComposerProps = {
    onSubmit?: (value: string, files: File[]) => Promise<void> | void;
    onValueChange?: (value: string) => void;
    placeholder?: string;
    value?: string;
    variant?: AiChatComposerVariant;
};

export function AiChatComposer({
    onSubmit,
    onValueChange,
    placeholder,
    value: controlledValue,
    variant = "chat",
}: AiChatComposerProps) {
    const [internalValue, setInternalValue] = useState("");
    const [files, setFiles] = useState<File[]>([]);
    const [submitting, setSubmitting] = useState(false);
    const [answerMode, setAnswerMode] = useState<AiChatComposerMode>("fast");
    const [activeTool, setActiveTool] = useState<AiChatComposerTool>("chat");
    const [selectedSkill, setSelectedSkill] = useState<AiChatComposerSkill | null>(null);
    const [panel, setPanel] = useState<AiChatComposerPanel>(null);
    const senderRef = useRef<HTMLElement>(null);
    const fileInputRef = useRef<HTMLInputElement>(null);
    const value = controlledValue ?? internalValue;
    const updateValue = (nextValue: string) => {
        if (controlledValue === undefined) {
            setInternalValue(nextValue);
        }

        onValueChange?.(nextValue);
    };
    const defaultPlaceholder =
        variant === "work" ? "发消息或创建任务... / 使用技能 @ 添加资料" : AI_CHAT_TOOL_PLACEHOLDERS[activeTool];
    const resolvedPlaceholder =
        placeholder ?? (selectedSkill ? `使用「${selectedSkill.label}」，输入你的需求` : defaultPlaceholder);

    const focusInput = () => {
        requestAnimationFrame(() => senderRef.current?.focus());
    };

    const submit = async (promptValue: string) => {
        const prompt = promptValue.trim();

        if (!prompt || submitting) {
            return;
        }

        setSubmitting(true);

        try {
            if (onSubmit) {
                await onSubmit(prompt, files);
            } else {
                void MessagePlugin.info("发送接口待接入");
            }

            updateValue("");
            setFiles([]);
        } catch {
            return;
        } finally {
            setSubmitting(false);
        }
    };

    const handleSend = (event: CustomEvent<TdChatSenderParams>) => {
        void submit(event.detail.value);
    };

    const handleAddAction = (action: AiChatComposerAddAction) => {
        setPanel(null);

        if (action === "upload") {
            fileInputRef.current?.click();
            return;
        }

        void MessagePlugin.info(`${ADD_ACTION_LABELS[action]}接口待接入`);
    };

    const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
        if (event.currentTarget.files?.length) {
            setFiles((current) => [...current, ...Array.from(event.currentTarget.files ?? [])]);
        }

        event.currentTarget.value = "";
    };

    const handleToolChange = (tool: AiChatComposerTool) => {
        setActiveTool(tool);
        setSelectedSkill(null);
        setPanel(null);
        focusInput();
    };

    const handleSelectSkill = (skill: AiChatComposerSkill) => {
        setSelectedSkill(skill);
        setActiveTool("chat");
        setPanel(null);
        updateValue("");
        focusInput();
    };
    const togglePanel = (nextPanel: Exclude<AiChatComposerPanel, null>) => {
        setPanel((currentPanel) => (currentPanel === nextPanel ? null : nextPanel));
    };
    const toolbar = (
        <ChatInputToolbar
            activeTool={activeTool}
            hasValue={value.trim().length > 0}
            mode={answerMode}
            onModeChange={setAnswerMode}
            onSend={() => submit(value)}
            onToggleAddPanel={() => togglePanel("add")}
            onToggleSkillsPanel={() => togglePanel("skills")}
            onToolChange={handleToolChange}
            onUnavailableAction={(label) => void MessagePlugin.info(`${label}接口待接入`)}
            onVoice={() => void MessagePlugin.info("语音输入接口待接入")}
            panel={panel}
            variant={variant}
        />
    );

    return (
        <section
            aria-label="AI 对话输入框"
            className={`${styles.composer} ${styles.theme} ${variant === "work" ? styles.work : ""}`}
            onKeyDown={(event) => {
                if (event.key === "Escape" && panel) {
                    event.preventDefault();
                    setPanel(null);
                }
            }}
        >
            <input
                accept="image/*,.pdf,.doc,.docx,.md,.ppt,.pptx,.txt,.xls,.xlsx"
                hidden
                multiple
                onChange={handleFileChange}
                ref={fileInputRef}
                type="file"
            />
            <div className={`${styles.surface} ${panel ? styles.surfaceActive : ""}`}>
                {files.length > 0 ? (
                    <div className={styles.attachmentList}>
                        {files.map((file, index) => (
                            <Tag
                                closable
                                key={`${file.name}-${index}`}
                                onClose={() =>
                                    setFiles((current) => current.filter((_, fileIndex) => fileIndex !== index))
                                }
                            >
                                {file.name}
                            </Tag>
                        ))}
                    </div>
                ) : null}
                <ChatSenderWithSlots
                    actions={false}
                    autosize={{ maxRows: 7, minRows: 1 }}
                    className={`${styles.sender} ${variant === "work" ? styles.senderWork : ""}`}
                    footerPrefix={toolbar}
                    loading={submitting}
                    onChange={(event) => updateValue(event.detail)}
                    onSend={handleSend}
                    placeholder={resolvedPlaceholder}
                    ref={senderRef}
                    value={value}
                />
            </div>
            {panel ? (
                <div className={styles.panelOverlay}>
                    {panel === "skills" ? (
                        <ChatInputSkillPanel onSelect={handleSelectSkill} />
                    ) : (
                        <ChatInputAddPanel onSelect={handleAddAction} />
                    )}
                </div>
            ) : null}
        </section>
    );
}

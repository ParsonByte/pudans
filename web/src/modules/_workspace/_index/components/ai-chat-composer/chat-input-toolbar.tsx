"use client";

import {
    ArrowUp,
    AtSign,
    AudioLines,
    Boxes,
    ChevronRight,
    ChevronsRight,
    FolderOpen,
    Image,
    MessageCircle,
    Mic,
    Monitor,
    Plug,
    Plus,
    SquarePen,
    SquarePlay,
} from "lucide-react";
import { Button } from "tdesign-react";

import styles from "../../styles/ai-chat-composer.module.css";
import { ChatInputModeMenu } from "./chat-input-mode-menu";
import { ChatInputModelMenu } from "./chat-input-model-menu";
import type {
    AiChatComposerMode,
    AiChatComposerPanel,
    AiChatComposerTool,
    AiChatComposerVariant,
} from "./chat-input-types";

const TOOL_OPTIONS: ReadonlyArray<{
    icon: React.ReactNode;
    label: string;
    submenu?: boolean;
    value: AiChatComposerTool;
}> = [
    { icon: <MessageCircle size={16} strokeWidth={1.8} />, label: "对话", value: "chat" },
    { icon: <FolderOpen size={16} strokeWidth={1.8} />, label: "项目", submenu: true, value: "project" },
    { icon: <SquarePlay size={16} strokeWidth={1.8} />, label: "视频生成", value: "video" },
    { icon: <Image size={16} strokeWidth={1.8} />, label: "图像生成", value: "image" },
    { icon: <SquarePen size={16} strokeWidth={1.8} />, label: "帮我写作", value: "writing" },
    { icon: <AtSign size={16} strokeWidth={1.8} />, label: "AI 播客", value: "podcast" },
    { icon: <AudioLines size={16} strokeWidth={1.8} />, label: "录音转写", value: "transcription" },
];

const WORK_ACTIONS: ReadonlyArray<{
    icon: React.ReactNode;
    label: string;
    value: "cloud-computer" | "plugin" | "project" | "skills";
}> = [
    { icon: <Monitor size={17} strokeWidth={1.8} />, label: "云电脑", value: "cloud-computer" },
    { icon: <FolderOpen size={17} strokeWidth={1.8} />, label: "项目", value: "project" },
    { icon: <Boxes size={17} strokeWidth={1.8} />, label: "技能", value: "skills" },
    { icon: <Plug size={17} strokeWidth={1.8} />, label: "插件", value: "plugin" },
];

type ChatInputToolbarProps = {
    activeTool: AiChatComposerTool;
    hasValue: boolean;
    mode: AiChatComposerMode;
    onModeChange: (mode: AiChatComposerMode) => void;
    onSend: () => void;
    onToggleAddPanel: () => void;
    onToggleSkillsPanel: () => void;
    onToolChange: (tool: AiChatComposerTool) => void;
    onUnavailableAction: (label: string) => void;
    onVoice: () => void;
    panel: AiChatComposerPanel;
    variant: AiChatComposerVariant;
};

export function ChatInputToolbar({
    activeTool,
    hasValue,
    mode,
    onModeChange,
    onSend,
    onToggleAddPanel,
    onToggleSkillsPanel,
    onToolChange,
    onUnavailableAction,
    onVoice,
    panel,
    variant,
}: ChatInputToolbarProps) {
    return (
        <div className={styles.toolbar}>
            <Button
                aria-expanded={panel === "add"}
                aria-label="添加文件或选择工具"
                className={`${styles.addButton} ${panel === "add" ? styles.addButtonActive : ""}`}
                onClick={onToggleAddPanel}
                type="button"
                variant="text"
            >
                <Plus size={20} strokeWidth={1.8} />
            </Button>

            <span aria-hidden className={styles.toolbarDivider} />

            {variant === "work" ? (
                <div className={styles.workToolbarMain}>
                    {WORK_ACTIONS.map((action) => {
                        const skillsOpen = action.value === "skills" && panel === "skills";

                        return (
                            <Button
                                aria-expanded={action.value === "skills" ? panel === "skills" : undefined}
                                className={`${styles.workToolButton} ${
                                    action.value === "cloud-computer" ? styles.workToolButtonActive : ""
                                } ${skillsOpen ? styles.workToolButtonOpen : ""}`}
                                key={action.value}
                                onClick={() => {
                                    if (action.value === "skills") {
                                        onToggleSkillsPanel();
                                        return;
                                    }

                                    onUnavailableAction(action.label);
                                }}
                                type="button"
                                variant="text"
                            >
                                {action.icon}
                                {action.label}
                                {action.value !== "cloud-computer" ? (
                                    <ChevronRight className={styles.toolChevron} size={14} strokeWidth={1.8} />
                                ) : null}
                            </Button>
                        );
                    })}
                </div>
            ) : (
                <div className={styles.toolbarMain}>
                    {TOOL_OPTIONS.map((tool) => (
                        <Button
                            aria-pressed={activeTool === tool.value}
                            className={`${styles.toolButton} ${activeTool === tool.value ? styles.toolButtonActive : ""}`}
                            key={tool.value}
                            onClick={() => onToolChange(tool.value)}
                            type="button"
                            variant="text"
                        >
                            {tool.icon}
                            {tool.label}
                            {tool.submenu ? (
                                <ChevronRight className={styles.toolChevron} size={14} strokeWidth={1.8} />
                            ) : null}
                        </Button>
                    ))}

                    <Button
                        aria-expanded={panel === "skills"}
                        aria-label="更多技能"
                        className={`${styles.moreButton} ${panel === "skills" ? styles.moreButtonActive : ""}`}
                        onClick={onToggleSkillsPanel}
                        type="button"
                        variant="text"
                    >
                        <ChevronsRight size={16} strokeWidth={1.8} />
                        更多技能
                    </Button>
                </div>
            )}

            <div className={styles.toolbarEnd}>
                {variant === "work" ? (
                    <ChatInputModelMenu />
                ) : (
                    <ChatInputModeMenu onChange={onModeChange} value={mode} />
                )}
                <Button
                    aria-label={hasValue ? "发送消息" : "语音输入"}
                    className={styles.actionButton}
                    onClick={hasValue ? onSend : onVoice}
                    type="button"
                    variant="text"
                >
                    {hasValue ? <ArrowUp size={18} strokeWidth={2} /> : <Mic size={18} strokeWidth={1.8} />}
                </Button>
            </div>
        </div>
    );
}

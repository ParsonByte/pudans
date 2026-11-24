"use client";

import { type ReactNode, useEffect, useState } from "react";

import {
    CalendarDays,
    ClipboardCheck,
    Cloud,
    Database,
    FileText,
    Music2,
    PenTool,
    Presentation,
    Table2,
} from "lucide-react";
import { Button, Empty, Input } from "tdesign-react";

import styles from "../../styles/ai-chat-composer.module.css";
import type { AiChatComposerSkill } from "./chat-input-types";

type SkillTone = "amber" | "blue" | "cyan" | "green" | "orange" | "purple" | "red" | "teal";

type SkillDefinition = AiChatComposerSkill & {
    icon: ReactNode;
    quick?: boolean;
    tone: SkillTone;
};

const SKILLS: readonly SkillDefinition[] = [
    {
        icon: <Music2 size={17} strokeWidth={1.8} />,
        id: "music-generation",
        label: "音乐生成",
        quick: true,
        tone: "purple",
    },
    {
        icon: <Presentation size={17} strokeWidth={1.8} />,
        id: "ppt-generation",
        label: "PPT 生成",
        quick: true,
        tone: "red",
    },
    {
        icon: <Table2 size={17} strokeWidth={1.8} />,
        id: "ai-table",
        label: "AI 表格",
        quick: true,
        tone: "blue",
    },
    {
        description: "帮你查看和编辑文档中的画板，支持把架构、流程、关系绘制成图形",
        icon: <PenTool size={17} strokeWidth={1.8} />,
        id: "board",
        label: "画板",
        tone: "purple",
    },
    {
        description: "帮你创建、上传、查看和编辑 Markdown 文件",
        icon: <FileText size={17} strokeWidth={1.8} />,
        id: "markdown-notes",
        label: "Markdown 笔记",
        tone: "orange",
    },
    {
        description: "帮你在知识库里查找、整理文档，管理空间和成员",
        icon: <Database size={17} strokeWidth={1.8} />,
        id: "knowledge-base",
        label: "知识库",
        tone: "blue",
    },
    {
        description: "帮你管理飞书任务，可创建待办任务、查看和更新任务状态",
        icon: <ClipboardCheck size={17} strokeWidth={1.8} />,
        id: "tasks",
        label: "任务",
        tone: "amber",
    },
    {
        description: "帮你撰写、编辑、排版和优化云文档",
        icon: <Cloud size={17} strokeWidth={1.8} />,
        id: "cloud-docs",
        label: "云文档",
        tone: "green",
    },
    {
        description: "帮你生成、编辑和美化 PPT",
        icon: <Presentation size={17} strokeWidth={1.8} />,
        id: "ppt",
        label: "PPT",
        tone: "cyan",
    },
    {
        description: "帮你查询会议纪要、妙记与产物",
        icon: <CalendarDays size={17} strokeWidth={1.8} />,
        id: "meetings",
        label: "会议",
        tone: "teal",
    },
    {
        description: "帮你搭建和管理多维表格，支持记录读写、报表计算和图表分析",
        icon: <Table2 size={17} strokeWidth={1.8} />,
        id: "bitable",
        label: "多维表格",
        tone: "teal",
    },
];

const SKILL_SHORTCUTS = new Map(SKILLS.map((skill, index) => [skill.id, index + 1]));

type ChatInputSkillPanelProps = {
    onSelect: (skill: AiChatComposerSkill) => void;
};

function matchesQuery(skill: SkillDefinition, query: string): boolean {
    const normalizedQuery = query.trim().toLocaleLowerCase();

    if (!normalizedQuery) {
        return true;
    }

    return `${skill.label} ${skill.description ?? ""}`.toLocaleLowerCase().includes(normalizedQuery);
}

export function ChatInputSkillPanel({ onSelect }: ChatInputSkillPanelProps) {
    const [query, setQuery] = useState("");
    const visibleSkills = SKILLS.filter((skill) => matchesQuery(skill, query));
    const quickSkills = visibleSkills.filter((skill) => skill.quick);
    const normalSkills = visibleSkills.filter((skill) => !skill.quick);
    const selectSkill = (skill: SkillDefinition) => {
        onSelect({ description: skill.description, id: skill.id, label: skill.label });
        setQuery("");
    };

    useEffect(() => {
        const selectByShortcut = (event: KeyboardEvent) => {
            if (!(event.metaKey || event.ctrlKey) || event.altKey || event.shiftKey) {
                return;
            }

            const index = Number.parseInt(event.key, 10);

            if (!Number.isInteger(index) || index < 1 || index > 9) {
                return;
            }

            const skill = SKILLS[index - 1];

            if (!skill) {
                return;
            }

            event.preventDefault();
            onSelect({ description: skill.description, id: skill.id, label: skill.label });
            setQuery("");
        };

        window.addEventListener("keydown", selectByShortcut);

        return () => window.removeEventListener("keydown", selectByShortcut);
    }, [onSelect]);

    return (
        <div className={styles.skillPanel}>
            <div className={styles.skillScroll}>
                {quickSkills.length > 0 ? (
                    <>
                        <span className={styles.sectionLabel}>快捷功能 ({quickSkills.length})</span>
                        {quickSkills.map((skill) => (
                            <Button
                                className={styles.skillRow}
                                key={skill.id}
                                onClick={() => selectSkill(skill)}
                                type="button"
                                variant="text"
                            >
                                <span className={`${styles.skillIcon} ${styles.skillIconQuick}`}>{skill.icon}</span>
                                <span className={styles.skillTitle}>{skill.label}</span>
                                <span aria-hidden className={styles.skillShortcut}>
                                    ⌘ {SKILL_SHORTCUTS.get(skill.id)}
                                </span>
                            </Button>
                        ))}
                    </>
                ) : null}

                {normalSkills.length > 0 ? (
                    <>
                        <span className={styles.sectionLabel}>技能 ({normalSkills.length})</span>
                        {normalSkills.map((skill) => (
                            <Button
                                className={styles.skillRow}
                                key={skill.id}
                                onClick={() => selectSkill(skill)}
                                type="button"
                                variant="text"
                            >
                                <span className={styles.skillIcon} data-tone={skill.tone}>
                                    {skill.icon}
                                </span>
                                <span className={styles.skillTitle}>{skill.label}</span>
                                <span className={styles.skillDescription}>{skill.description}</span>
                                {SKILL_SHORTCUTS.get(skill.id)! <= 9 ? (
                                    <span aria-hidden className={styles.skillShortcut}>
                                        ⌘ {SKILL_SHORTCUTS.get(skill.id)}
                                    </span>
                                ) : null}
                            </Button>
                        ))}
                    </>
                ) : null}

                {visibleSkills.length === 0 ? (
                    <Empty className={styles.skillEmpty} description="没有找到相关技能" />
                ) : null}
            </div>

            <div className={styles.skillSearch}>
                <Input onChange={(value) => setQuery(String(value))} placeholder="搜索..." value={query} />
            </div>
        </div>
    );
}

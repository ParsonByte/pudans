"use client";

import type { ReactNode } from "react";

import Link from "next/link";

import { useQuery } from "@tanstack/react-query";
import {
    AiIcon,
    ChartBubbleIcon,
    FileIcon,
    FolderIcon,
    TerminalIcon,
    UsercaseIcon,
    VideoIcon,
} from "tdesign-icons-react";
import { Alert } from "tdesign-react";

import { SectionCard } from "@/components/ui";

import { formatCountOrDash, formatDateTime } from "../../../composables/_format";
import { DansPage } from "../components/page";
import {
    getAgentSessionSummary,
    getAssetFacets,
    listAiChannels,
    listAiModels,
    listGenerationTasks,
    listMemoryPool,
    listSandboxes,
} from "../composables/_dans";

const DANS_MENU_PATHS = {
    aiBilling: "/workspace/console?dans=ai-billing",
    aiCatalog: "/workspace/console?dans=ai-catalog",
    aiChannels: "/workspace/console?dans=ai-channels",
    aiModels: "/workspace/console?dans=ai-models",
    modelPlaza: "/workspace/console?dans=model-plaza",
    agentMemory: "/workspace/console?dans=agent-memory",
    agentSandboxes: "/workspace/console?dans=agent-sandboxes",
    agentSessions: "/workspace/console?dans=agent-sessions",
    creationAssets: "/workspace/console?dans=creation-assets",
    creationContent: "/workspace/console?dans=creation-content",
    creationTasks: "/workspace/console?dans=creation-tasks",
} as const;

export function DansOverviewView() {
    const channelsQuery = useQuery({ queryFn: listAiChannels, queryKey: ["dans", "ai", "channels"] });
    const modelsQuery = useQuery({ queryFn: listAiModels, queryKey: ["dans", "ai", "models"] });
    const agentSummaryQuery = useQuery({
        queryFn: getAgentSessionSummary,
        queryKey: ["dans", "agent", "session-summary"],
    });
    const sandboxesQuery = useQuery({ queryFn: () => listSandboxes(100), queryKey: ["dans", "agent", "sandboxes"] });
    const memoryPoolQuery = useQuery({
        queryFn: () => listMemoryPool("published", 100),
        queryKey: ["dans", "agent", "memory-pool", "published"],
    });
    const generationTasksQuery = useQuery({
        queryFn: () => listGenerationTasks(100),
        queryKey: ["dans", "creation", "generation-tasks"],
    });
    const assetFacetsQuery = useQuery({
        queryFn: getAssetFacets,
        queryKey: ["dans", "creation", "asset-facets"],
    });
    const failedQueries = [
        channelsQuery,
        modelsQuery,
        agentSummaryQuery,
        sandboxesQuery,
        memoryPoolQuery,
        generationTasksQuery,
        assetFacetsQuery,
    ].filter((query) => query.isError);
    const metrics = [
        { label: "AI 渠道", value: formatCountOrDash(channelsQuery.data?.length) },
        { label: "逻辑模型", value: formatCountOrDash(modelsQuery.data?.length) },
        { label: "会话总数", value: formatCountOrDash(agentSummaryQuery.data?.total) },
        { label: "沙箱实例", value: formatCountOrDash(sandboxesQuery.data?.items?.length) },
        { label: "已发布记忆", value: formatCountOrDash(memoryPoolQuery.data?.length) },
        { label: "生成任务", value: formatCountOrDash(generationTasksQuery.data?.items?.length) },
        { label: "资产总量", value: formatCountOrDash(assetFacetsQuery.data?.total) },
        { label: "最近会话时间", value: formatDateTime(agentSummaryQuery.data?.latestMessageAt) },
    ];

    return (
        <DansPage
            description="跨 AI、Agent 和创作域的管控入口。这里展示平台范围真实接口数据。"
            icon={<ChartBubbleIcon size={22} />}
            metrics={metrics}
            section="总览"
            title="DANS 概览"
        >
            {failedQueries.length > 0 ? (
                <Alert
                    message={`${failedQueries.length} 项统计数据加载失败，其余指标仍按真实接口展示。`}
                    theme="warning"
                />
            ) : null}
            <div className="grid min-w-0 grid-cols-1 gap-4 xl:grid-cols-3">
                <SectionCard
                    extra={
                        <Link className="text-sm text-[var(--td-brand-color)]" href={DANS_MENU_PATHS.aiChannels}>
                            进入 AI
                        </Link>
                    }
                    title={
                        <span className="flex items-center gap-2">
                            <AiIcon size={18} />
                            AI 能力
                        </span>
                    }
                >
                    <div className="flex flex-col gap-3 text-sm">
                        <p className="m-0 text-[var(--td-text-color-secondary)]">
                            AI 服务商、路由、计费和能力目录统一由 AI 配置与目录接口提供。
                        </p>
                        <DansShortcut href={DANS_MENU_PATHS.aiChannels} label="AI 服务商" />
                        <DansShortcut href={DANS_MENU_PATHS.aiModels} label="模型与路由" />
                        <DansShortcut href={DANS_MENU_PATHS.modelPlaza} label="模型广场" />
                        <DansShortcut href={DANS_MENU_PATHS.aiBilling} label="计费与配额" />
                        <DansShortcut href={DANS_MENU_PATHS.aiCatalog} label="能力目录" />
                    </div>
                </SectionCard>
                <SectionCard
                    extra={
                        <Link className="text-sm text-[var(--td-brand-color)]" href={DANS_MENU_PATHS.agentSessions}>
                            进入 Agent
                        </Link>
                    }
                    title={
                        <span className="flex items-center gap-2">
                            <TerminalIcon size={18} />
                            Agent 运行
                        </span>
                    }
                >
                    <div className="flex flex-col gap-3 text-sm">
                        <p className="m-0 text-[var(--td-text-color-secondary)]">
                            会话与沙箱按平台范围返回；记忆池按审核权限返回真实候选与已发布条目。
                        </p>
                        <DansShortcut href={DANS_MENU_PATHS.agentSessions} label="会话与运行" />
                        <DansShortcut href={DANS_MENU_PATHS.agentSandboxes} label="沙箱" />
                        <DansShortcut href={DANS_MENU_PATHS.agentMemory} label="记忆与理解" />
                    </div>
                </SectionCard>
                <SectionCard
                    extra={
                        <Link className="text-sm text-[var(--td-brand-color)]" href={DANS_MENU_PATHS.creationTasks}>
                            进入创作
                        </Link>
                    }
                    title={
                        <span className="flex items-center gap-2">
                            <VideoIcon size={18} />
                            创作内容
                        </span>
                    }
                >
                    <div className="flex flex-col gap-3 text-sm">
                        <p className="m-0 text-[var(--td-text-color-secondary)]">
                            生成任务与资产按平台范围返回；作品、模板和活动使用管理范围内容接口。
                        </p>
                        <DansShortcut href={DANS_MENU_PATHS.creationTasks} label="生成任务" />
                        <DansShortcut href={DANS_MENU_PATHS.creationAssets} label="资产与主体" />
                        <DansShortcut href={DANS_MENU_PATHS.creationContent} label="内容运营" />
                    </div>
                </SectionCard>
            </div>
            <div className="grid min-w-0 grid-cols-1 gap-4 md:grid-cols-3">
                <SummaryCard
                    icon={<FolderIcon size={18} />}
                    label="已发布记忆"
                    value={formatCountOrDash(memoryPoolQuery.data?.length)}
                />
                <SummaryCard
                    icon={<UsercaseIcon size={18} />}
                    label="记忆池审核"
                    href="/workspace/console?dans=agent-memory"
                />
                <SummaryCard icon={<FileIcon size={18} />} label="内容运营" href={DANS_MENU_PATHS.creationContent} />
            </div>
        </DansPage>
    );
}

function DansShortcut({ href, label }: { href: string; label: string }) {
    return (
        <Link
            className="flex items-center justify-between rounded-lg bg-[var(--td-bg-color-secondarycontainer)] px-3 py-2 text-[var(--td-text-color-primary)] no-underline"
            href={href}
        >
            <span>{label}</span>
            <span aria-hidden className="text-[var(--td-text-color-placeholder)]">
                →
            </span>
        </Link>
    );
}

function SummaryCard({ href, icon, label, value }: { href?: string; icon: ReactNode; label: string; value?: string }) {
    const content = (
        <div className="flex items-center gap-3 rounded-xl bg-[var(--td-bg-color-secondarycontainer)] px-4 py-3">
            <span className="flex size-9 items-center justify-center rounded-lg bg-[var(--td-bg-color-container)] text-[var(--td-brand-color)]">
                {icon}
            </span>
            <span className="min-w-0">
                <span className="block text-xs text-[var(--td-text-color-secondary)]">{label}</span>
                <span className="block text-sm font-semibold text-[var(--td-text-color-primary)]">
                    {value ?? "查看"}
                </span>
            </span>
        </div>
    );

    return href ? (
        <Link className="no-underline" href={href}>
            {content}
        </Link>
    ) : (
        content
    );
}

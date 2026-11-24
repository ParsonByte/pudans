"use client";

import { useMemo } from "react";

import dynamic from "next/dynamic";

import type { AuditSessionSummary } from "@pudans-dev/api-client";
import { ArrowRightIcon, ChatIcon, FileIcon, RefreshIcon, TimeIcon, UserListIcon } from "tdesign-icons-react";
import { Button, Empty, Skeleton, Tag } from "tdesign-react";

import { StatTile } from "@/components/ui";

import { formatCount, operationLabel } from "../../../../composables/_format";
import { formatJourneyDuration } from "../composables/_dashboard";
import { useDashboardTheme } from "../composables/_theme";

const JourneyRelationshipGraph = dynamic(
    () => import("./journey-relationship-graph").then((module) => module.JourneyRelationshipGraph),
    {
        loading: () => <Skeleton theme="paragraph" />,
        ssr: false,
    },
);

type RecentUserJourneysProps = {
    error: boolean;
    isPending: boolean;
    onRetry: () => void;
    sessions: AuditSessionSummary[];
    truncated: boolean;
};

function topPath(counts: Map<string, number>): { count: number; path: string } | undefined {
    const best = [...counts.entries()].sort((left, right) => right[1] - left[1])[0];

    return best ? { count: best[1], path: best[0] } : undefined;
}

export function RecentUserJourneys({ error, isPending, onRetry, sessions, truncated }: RecentUserJourneysProps) {
    const { token, tone } = useDashboardTheme();
    const summary = useMemo(() => {
        const users = new Set<string>();
        const entries = new Map<string, number>();
        const exits = new Map<string, number>();
        const operations = new Set<string>();
        let actionCount = 0;
        let durationMs = 0;
        let pageViews = 0;

        for (const session of sessions) {
            const key = session.uid ?? session.username ?? "unknown";
            users.add(key);
            actionCount += session.actionCount ?? 0;
            durationMs += session.durationMs ?? 0;
            pageViews += session.pageCount ?? 0;
            if (session.entryPath) {
                entries.set(session.entryPath, (entries.get(session.entryPath) ?? 0) + 1);
            }
            if (session.exitPath) {
                exits.set(session.exitPath, (exits.get(session.exitPath) ?? 0) + 1);
            }
            for (const operation of session.operations ?? []) {
                operations.add(operation);
            }
        }

        return {
            actionCount,
            averageDuration: sessions.length > 0 ? durationMs / sessions.length : 0,
            entry: topPath(entries),
            exit: topPath(exits),
            operations: [...operations],
            pageViews,
            userCount: users.size,
        };
    }, [sessions]);
    const legend = [
        { color: tone("blue").base, icon: UserListIcon, label: "用户" },
        { color: tone("geekblue").base, icon: ChatIcon, label: "会话" },
        { color: tone("gold").base, icon: FileIcon, label: "页面" },
    ];

    if (isPending) {
        return (
            <div className="grid gap-4">
                <Skeleton theme="paragraph" />
                <Skeleton theme="paragraph" />
            </div>
        );
    }

    if (error) {
        return (
            <Empty
                action={
                    <Button icon={<RefreshIcon />} onClick={onRetry}>
                        重新加载
                    </Button>
                }
                description="用户追溯加载失败"
            />
        );
    }

    if (sessions.length === 0) {
        return <Empty description="暂无用户追溯数据" />;
    }

    return (
        <div className="space-y-4">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                <div className="flex min-w-0 flex-1 items-start gap-3">
                    <span
                        className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full"
                        style={{ background: token.colorPrimaryBg, color: token.colorPrimary }}
                    >
                        <UserListIcon size={24} />
                    </span>
                    <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                            <span className="text-lg font-semibold" style={{ color: token.colorText }}>
                                全部用户
                            </span>
                            <Tag theme="primary" variant="light-outline">
                                {formatCount(summary.userCount)} 位用户 · {formatCount(sessions.length)} 个会话
                            </Tag>
                        </div>
                        <div
                            className="mt-1 flex flex-col gap-1 text-xs sm:flex-row sm:flex-wrap sm:items-center sm:gap-x-3"
                            style={{ color: token.colorTextSecondary }}
                        >
                            <span className="flex items-center gap-1">
                                <TimeIcon size={13} />
                                平均会话 {formatJourneyDuration(summary.averageDuration)}
                            </span>
                            <span>
                                {formatCount(summary.pageViews)} 次页面访问 · {formatCount(summary.actionCount)} 次操作
                            </span>
                        </div>
                    </div>
                </div>
                {truncated ? (
                    <Tag className="shrink-0" theme="warning" variant="light-outline">
                        数据已达到服务端上限
                    </Tag>
                ) : null}
            </div>

            <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_292px]">
                <div
                    className="min-w-0 overflow-hidden rounded-2xl border p-4"
                    style={{ background: token.colorFillQuaternary, borderColor: token.colorBorderSecondary }}
                >
                    <div className="mb-2 flex flex-col gap-2 px-1 sm:flex-row sm:items-center sm:justify-between">
                        <div>
                            <div className="text-sm font-semibold" style={{ color: token.colorText }}>
                                访问关系图
                            </div>
                            <div className="mt-0.5 text-[11px]" style={{ color: token.colorTextTertiary }}>
                                全部用户、会话与入口 / 出口页面之间的关系
                            </div>
                        </div>
                        <div className="flex flex-wrap items-center gap-3 text-[11px]">
                            {legend.map((item) => {
                                const Icon = item.icon;

                                return (
                                    <span
                                        className="flex items-center gap-1.5"
                                        key={item.label}
                                        style={{ color: token.colorTextSecondary }}
                                    >
                                        <span
                                            className="flex h-5 w-5 items-center justify-center rounded-full"
                                            style={{ background: `${item.color}18`, color: item.color }}
                                        >
                                            <Icon size={13} />
                                        </span>
                                        {item.label}
                                    </span>
                                );
                            })}
                        </div>
                    </div>
                    <JourneyRelationshipGraph sessions={sessions} />
                </div>

                <div className="space-y-3">
                    <div className="grid grid-cols-2 gap-2">
                        <StatTile compact label="用户" value={formatCount(summary.userCount)} />
                        <StatTile compact label="会话" value={formatCount(sessions.length)} />
                        <StatTile compact label="页面访问" value={formatCount(summary.pageViews)} />
                        <StatTile compact label="操作总数" value={formatCount(summary.actionCount)} />
                    </div>
                    <div className="rounded-2xl border p-4" style={{ borderColor: token.colorBorderSecondary }}>
                        <div className="text-xs font-semibold" style={{ color: token.colorText }}>
                            入口 / 出口
                        </div>
                        <div className="mt-3">
                            <div className="flex items-center justify-between gap-2 text-[11px]">
                                <span style={{ color: token.colorTextTertiary }}>主要入口</span>
                                <span style={{ color: token.colorTextTertiary }}>
                                    {formatCount(summary.entry?.count ?? 0)} 个会话
                                </span>
                            </div>
                            <div
                                className="mt-1 text-xs leading-5 break-all"
                                style={{ color: token.colorTextSecondary }}
                            >
                                {summary.entry?.path ?? "—"}
                            </div>
                            <div
                                className="my-3 flex items-center gap-1.5 text-[11px]"
                                style={{ color: token.colorTextTertiary }}
                            >
                                <ArrowRightIcon size={13} />
                                会话路径
                            </div>
                            <div className="flex items-center justify-between gap-2 text-[11px]">
                                <span style={{ color: token.colorTextTertiary }}>主要出口</span>
                                <span style={{ color: token.colorTextTertiary }}>
                                    {formatCount(summary.exit?.count ?? 0)} 个会话
                                </span>
                            </div>
                            <div
                                className="mt-1 text-xs leading-5 break-all"
                                style={{ color: token.colorTextSecondary }}
                            >
                                {summary.exit?.path ?? "—"}
                            </div>
                        </div>
                    </div>
                    <div className="rounded-2xl border p-4" style={{ borderColor: token.colorBorderSecondary }}>
                        <div className="flex items-center justify-between gap-2">
                            <span className="text-xs font-semibold" style={{ color: token.colorText }}>
                                操作类型
                            </span>
                            <span className="text-[11px]" style={{ color: token.colorTextTertiary }}>
                                {formatCount(summary.operations.length)} 类
                            </span>
                        </div>
                        <div className="mt-3 flex flex-wrap gap-1.5">
                            {summary.operations.length > 0 ? (
                                summary.operations.map((operation) => (
                                    <Tag key={operation} size="small" variant="light-outline">
                                        {operationLabel(operation)}
                                    </Tag>
                                ))
                            ) : (
                                <span className="text-xs" style={{ color: token.colorTextTertiary }}>
                                    暂无操作记录
                                </span>
                            )}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}

"use client";

import { useMemo, useState } from "react";

import dynamic from "next/dynamic";

import { type AuditJourney, apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";
import { Button, Empty, Select, Skeleton, Tag } from "antd";
import { Clock3, RefreshCw, UserRound } from "lucide-react";

import { ResultTag } from "../../../../components/result-tag";
import { formatDateTime } from "../../../../composables/_format";
import { formatCount, formatJourneyDuration, operationLabel, userLabel } from "../composables/_dashboard";
import { buildJourneySessions } from "../composables/_journey";
import { useDashboardTheme } from "../composables/_theme";

const JourneyRelationshipGraph = dynamic(
    () => import("./journey-relationship-graph").then((module) => module.JourneyRelationshipGraph),
    {
        loading: () => <Skeleton active paragraph={{ rows: 6 }} />,
        ssr: false,
    },
);

type RecentUserJourneysProps = {
    error: boolean;
    from: string;
    isPending: boolean;
    journeys: AuditJourney[];
    onRetry: () => void;
};

function journeyKey(journey: AuditJourney, index: number): string {
    return journey.uid ?? journey.username ?? `journey-${index}`;
}

function TraceStat({ label, value }: { label: string; value: string }) {
    const { token } = useDashboardTheme();

    return (
        <div
            className="rounded-2xl border px-3 py-2.5"
            style={{
                background: token.colorFillQuaternary,
                borderColor: token.colorBorderSecondary,
            }}
        >
            <div className="text-[11px]" style={{ color: token.colorTextTertiary }}>
                {label}
            </div>
            <div className="mt-1 text-sm font-semibold" style={{ color: token.colorText }}>
                {value}
            </div>
        </div>
    );
}

export function RecentUserJourneys({ error, from, isPending, journeys, onRetry }: RecentUserJourneysProps) {
    const { token } = useDashboardTheme();
    const [selectedKey, setSelectedKey] = useState<string>();
    const [sessionKey, setSessionKey] = useState("latest");
    const selectedSummary =
        journeys.find((journey, index) => journeyKey(journey, index) === selectedKey) ?? journeys[0];
    const selectedJourneyKey = selectedSummary
        ? journeyKey(selectedSummary, journeys.indexOf(selectedSummary))
        : undefined;
    const detailQuery = useQuery({
        enabled: Boolean(selectedSummary?.uid),
        queryFn: async () => {
            const response = await apiClient.auditLog.listAuditJourneys({
                from,
                page: 1,
                size: 1,
                summary: false,
                uid: selectedSummary?.uid,
            });

            return (
                response.data.items?.find((journey) => journey.uid === selectedSummary?.uid) ?? response.data.items?.[0]
            );
        },
        queryKey: ["console", "dashboard", "journey-detail", from, selectedSummary?.uid],
        staleTime: 30_000,
    });
    const detail = detailQuery.data;
    const sessions = useMemo(() => buildJourneySessions(detail), [detail]);
    const latestSession =
        [...sessions].reverse().find((session) => session.pageCount > 0) ?? sessions[sessions.length - 1];
    const effectiveSessionKey =
        sessionKey === "all"
            ? "all"
            : sessionKey === "latest"
              ? String(latestSession?.index ?? "all")
              : sessions.some((session) => String(session.index) === sessionKey)
                ? sessionKey
                : String(latestSession?.index ?? "all");
    const selectorValue = sessionKey === "all" || sessionKey === "latest" ? sessionKey : effectiveSessionKey;
    const visibleSessions =
        effectiveSessionKey === "all"
            ? sessions
            : sessions.filter((session) => String(session.index) === effectiveSessionKey);
    const options = journeys.map((journey, index) => ({
        label: `${userLabel(journey.username ?? journey.uid)} · ${formatCount(journey.pageCount)} 个页面 · ${formatCount(journey.actionCount)} 次操作`,
        value: journeyKey(journey, index),
    }));
    const sessionOptions = useMemo(
        () => [
            {
                label: `最近页面会话 · ${latestSession ? `会话 ${latestSession.number} · 完整页面链路` : "暂无记录"}`,
                value: "latest",
            },
            {
                label: `全部 ${formatCount(sessions.length)} 个会话 · 全量关系网`,
                value: "all",
            },
            ...sessions
                .slice()
                .reverse()
                .map((session) => ({
                    label: `会话 ${session.number} · ${formatDateTime(session.start)} · ${formatCount(session.pageCount)} 页 / ${formatCount(session.actionCount)} 次操作`,
                    value: String(session.index),
                })),
        ],
        [latestSession, sessions],
    );

    if (isPending) {
        return (
            <div className="grid gap-4">
                <Skeleton active paragraph={{ rows: 4 }} />
                <Skeleton active paragraph={{ rows: 4 }} />
            </div>
        );
    }

    if (error) {
        return (
            <Empty description="用户链路加载失败">
                <Button icon={<RefreshCw size={14} />} onClick={onRetry}>
                    重新加载
                </Button>
            </Empty>
        );
    }

    if (journeys.length === 0) {
        return <Empty description="暂无可回溯的用户链路" image={Empty.PRESENTED_IMAGE_SIMPLE} />;
    }

    if (detailQuery.isPending) {
        return (
            <div className="grid gap-4">
                <Skeleton active paragraph={{ rows: 3 }} />
                <Skeleton active paragraph={{ rows: 8 }} />
            </div>
        );
    }

    if (detailQuery.isError || !detail) {
        return (
            <Empty description="用户完整链路加载失败">
                <Button icon={<RefreshCw size={14} />} onClick={() => void detailQuery.refetch()}>
                    重新加载
                </Button>
            </Empty>
        );
    }

    return (
        <div className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-2">
                    <span
                        className="flex h-8 w-8 items-center justify-center rounded-xl"
                        style={{ background: token.colorFillSecondary, color: token.colorPrimary }}
                    >
                        <UserRound size={15} />
                    </span>
                    <div>
                        <div className="text-xs font-semibold" style={{ color: token.colorText }}>
                            当前回溯用户
                        </div>
                        <div className="text-[11px]" style={{ color: token.colorTextTertiary }}>
                            选择用户查看完整访问足迹，可继续按会话聚焦
                        </div>
                    </div>
                </div>
                <div className="flex w-full flex-wrap items-center gap-2 sm:w-auto">
                    <Select
                        aria-label="选择回溯用户"
                        className="w-full sm:w-auto sm:min-w-72"
                        onChange={(value: string) => {
                            setSelectedKey(value);
                            setSessionKey("latest");
                        }}
                        optionFilterProp="label"
                        options={options}
                        showSearch
                        size="small"
                        value={selectedJourneyKey}
                    />
                    {sessions.length > 1 ? (
                        <Select
                            aria-label="选择回溯会话"
                            className="w-full sm:w-auto sm:min-w-72"
                            onChange={(value: string) => setSessionKey(value)}
                            options={sessionOptions}
                            size="small"
                            value={selectorValue}
                        />
                    ) : (
                        <span className="text-xs font-medium" style={{ color: token.colorTextSecondary }}>
                            {sessions[0] ? `会话 ${sessions[0].number}` : "暂无会话"}
                        </span>
                    )}
                </div>
            </div>

            <div
                className="rounded-3xl border p-4"
                style={{
                    background: token.colorFillQuaternary,
                    borderColor: token.colorBorderSecondary,
                }}
            >
                <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                    <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                            <span className="text-base font-semibold" style={{ color: token.colorText }}>
                                {userLabel(detail.username ?? detail.uid)}
                            </span>
                            <ResultTag result={detail.status} />
                            <Tag color="blue" variant="filled">
                                {formatCount(detail.sessionCount)} 个会话
                            </Tag>
                        </div>
                        <div className="mt-1 text-[11px]" style={{ color: token.colorTextTertiary }}>
                            UID {detail.uid ?? "—"} · 最近活动 {formatDateTime(detail.end)}
                        </div>
                    </div>
                    <div
                        className="text-left text-[11px] leading-5 lg:text-right"
                        style={{ color: token.colorTextSecondary }}
                    >
                        <div className="flex items-center gap-1.5 lg:justify-end">
                            <Clock3 size={13} />
                            {formatJourneyDuration(detail.durationMs)}
                        </div>
                        <div>
                            {formatDateTime(detail.start)} → {formatDateTime(detail.end)}
                        </div>
                    </div>
                </div>
                <div className="mt-4 grid grid-cols-1 gap-2 sm:grid-cols-2 md:grid-cols-4">
                    <TraceStat label="会话总数" value={formatCount(detail.sessionCount)} />
                    <TraceStat label="页面访问" value={formatCount(detail.pageCount)} />
                    <TraceStat label="操作明细" value={formatCount(detail.actionCount)} />
                    <TraceStat label="入口 → 出口" value={`${detail.entryPath ?? "—"} → ${detail.exitPath ?? "—"}`} />
                </div>
                <div className="mt-3 flex flex-wrap items-center gap-1.5">
                    <span className="mr-1 text-[11px]" style={{ color: token.colorTextTertiary }}>
                        全部操作类型
                    </span>
                    {(detail.operations ?? []).map((operation) => (
                        <Tag key={operation}>{operationLabel(operation)}</Tag>
                    ))}
                </div>
            </div>

            <JourneyRelationshipGraph journey={detail} sessions={visibleSessions} />
        </div>
    );
}

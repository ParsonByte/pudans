"use client";

import { useMemo, useState } from "react";

import type { AuditSessionSummary, ReplayStep } from "@pudans-dev/api-client";
import {
    ArrowLeftIcon,
    ArrowRightIcon,
    ErrorTriangleIcon,
    FlagIcon,
    FolderZipIcon,
    GitCommitIcon,
    LinkIcon,
    LoginIcon,
    LogoutIcon,
    MapRoutePlanningIcon,
    TimeIcon,
} from "tdesign-icons-react";
import { Button, Empty, Tag } from "tdesign-react";

import { ResultTag, ScrollArea } from "@/components/ui";

import { formatCount, formatDateTime, formatDuration, formatPath, operationLabel } from "../composables/_format";
import { useDashboardTheme } from "../modules/overview/dashboard/composables/_theme";
import { CopyableIdentifier } from "./copyable-identifier";
import { DescriptionGrid } from "./description-grid";

type TraceWaterfallProps = {
    session?: AuditSessionSummary;
    steps?: ReplayStep[];
};

type JourneyPage = {
    actions: ReplayStep[];
    entry: boolean;
    exit: boolean;
    index: number;
    step: ReplayStep;
};

const ASSOCIATED_STEP_LIMIT = 6;
const PAGE_DUPLICATE_WINDOW_MS = 10_000;
const ACTION_PREVIEW_LIMIT = 8;
const ACTION_EXPANSION_SIZE = 40;

function timestampOf(value?: string): number {
    if (!value) {
        return 0;
    }

    const timestamp = Date.parse(value);

    return Number.isNaN(timestamp) ? 0 : timestamp;
}

function startOf(step: ReplayStep): number {
    return timestampOf(step.timestamp) - Math.max(0, step.durationMs ?? 0);
}

function isPageStep(step: ReplayStep): boolean {
    if (step.resourceType === "Page") {
        return true;
    }

    return step.operation === "NAVIGATION" && Boolean(step.requestPath) && !step.requestPath?.startsWith("/apis/");
}

function pathOf(step?: ReplayStep): string {
    if (!step) {
        return "—";
    }

    return formatPath(step.resourceName || step.requestPath);
}

function stepTitle(step?: ReplayStep): string {
    if (!step) {
        return "—";
    }

    return isPageStep(step) ? pathOf(step) : operationLabel(step.operation);
}

function stepKey(step: ReplayStep, index: number): string {
    return step.auditId ?? `${step.timestamp ?? ""}-${step.stepIndex ?? ""}-${step.operation ?? ""}-${index}`;
}

function prettySnapshot(snapshot?: string): string {
    if (!snapshot) {
        return "";
    }

    try {
        return JSON.stringify(JSON.parse(snapshot), null, 2);
    } catch {
        return snapshot;
    }
}

function derivedStatus(steps: ReplayStep[]): string {
    if (steps.some((step) => step.result === "DENIED")) {
        return "DENIED";
    }
    if (steps.some((step) => step.result === "FAILED" || step.result === "FAILURE")) {
        return "FAILURE";
    }
    return "SUCCESS";
}

export function TraceWaterfall({ session, steps }: TraceWaterfallProps) {
    const { token, tone } = useDashboardTheme();
    const [selectedAuditId, setSelectedAuditId] = useState<string>();
    const [actionLimits, setActionLimits] = useState<Record<string, number>>({});
    const sorted = useMemo(
        () =>
            [...(steps ?? [])].sort(
                (left, right) =>
                    startOf(left) - startOf(right) ||
                    (left.stepIndex ?? Number.MAX_SAFE_INTEGER) - (right.stepIndex ?? Number.MAX_SAFE_INTEGER) ||
                    (left.auditId ?? "").localeCompare(right.auditId ?? ""),
            ),
        [steps],
    );

    if (sorted.length === 0) {
        return <Empty description="暂无可回放的会话步骤" size="small" />;
    }

    const pageNodes: JourneyPage[] = [];
    const prelude: ReplayStep[] = [];
    sorted.forEach((step, index) => {
        if (!isPageStep(step)) {
            if (pageNodes.length === 0) {
                prelude.push(step);
            } else {
                pageNodes[pageNodes.length - 1].actions.push(step);
            }
            return;
        }

        const previous = pageNodes[pageNodes.length - 1];
        const duplicate =
            previous &&
            pathOf(previous.step) === pathOf(step) &&
            startOf(step) - startOf(previous.step) <= PAGE_DUPLICATE_WINDOW_MS;
        if (duplicate) {
            previous.actions.push(step);
            return;
        }

        pageNodes.push({
            actions: [],
            entry: false,
            exit: false,
            index,
            step,
        });
    });
    if (pageNodes.length > 0) {
        pageNodes[0].entry = true;
        pageNodes[pageNodes.length - 1].exit = true;
    }

    const selectedStepIndex = sorted.findIndex((step) => step.auditId === selectedAuditId);
    const selectedIndex = selectedStepIndex >= 0 ? selectedStepIndex : 0;
    const selected = sorted[selectedIndex];
    const previous = selectedIndex > 0 ? sorted[selectedIndex - 1] : undefined;
    const next = selectedIndex < sorted.length - 1 ? sorted[selectedIndex + 1] : undefined;
    const selectedPage = pageNodes.find(
        (node) =>
            node.step.auditId === selected.auditId || node.actions.some((step) => step.auditId === selected.auditId),
    );
    const selectedIsPage = isPageStep(selected);
    const entryPath = session?.entryPath ?? pathOf(pageNodes[0]?.step ?? sorted[0]);
    const exitPath = session?.exitPath ?? pathOf(pageNodes[pageNodes.length - 1]?.step ?? sorted[sorted.length - 1]);
    const status = session?.status ?? derivedStatus(sorted);
    const pageCount = session?.pageCount ?? pageNodes.length;
    const actionCount =
        session?.actionCount ??
        sorted.filter((step) => !isPageStep(step) || pageNodes.some((node) => node.actions.includes(step))).length;
    const recordCount = session?.recordCount ?? sorted.length;
    const archivedCount = sorted.filter((step) => step.archived).length;
    const anomalyCount = sorted.filter(
        (step) => step.result === "FAILED" || step.result === "FAILURE" || step.result === "DENIED",
    ).length;
    const parentIndex = selected.parentAuditId
        ? sorted.findIndex((step) => step.auditId === selected.parentAuditId)
        : -1;
    const parent = parentIndex >= 0 ? sorted[parentIndex] : undefined;
    const children = sorted.filter((step) => Boolean(selected.auditId) && step.parentAuditId === selected.auditId);
    const visibleChildren = children.slice(0, ASSOCIATED_STEP_LIMIT);
    const selectedPageIndex = pageNodes.findIndex(
        (node) =>
            node.step.auditId === selected.auditId || node.actions.some((step) => step.auditId === selected.auditId),
    );
    const previousPage = selectedPageIndex > 0 ? pageNodes[selectedPageIndex - 1] : undefined;
    const nextPage =
        selectedPageIndex >= 0 ? pageNodes[selectedPageIndex + 1] : pageNodes.length > 0 ? pageNodes[0] : undefined;
    const pageSequence = pageNodes
        .slice(0, 4)
        .map((node) => pathOf(node.step))
        .join(" → ");
    const hiddenPageCount = Math.max(0, pageNodes.length - 4);
    const selectedDenied = selected.result === "DENIED";
    const selectedFailed = selected.result === "FAILED" || selected.result === "FAILURE";
    const selectedColor = selectedDenied
        ? tone("warning").base
        : selectedFailed
          ? tone("red").base
          : tone("green").base;
    const select = (step: ReplayStep) => setSelectedAuditId(step.auditId);
    const expandActions = (key: string, count: number) => {
        setActionLimits((current) => ({
            ...current,
            [key]: Math.min(count, (current[key] ?? ACTION_PREVIEW_LIMIT) + ACTION_EXPANSION_SIZE),
        }));
    };
    const actionRow = (step: ReplayStep, index: number, compact = false) => {
        const active = step.auditId === selected.auditId;
        const failed = step.result === "FAILED" || step.result === "FAILURE" || step.result === "DENIED";
        const path = [step.requestMethod, formatPath(step.requestPath)].filter(Boolean).join(" ") || "—";

        const dotColor =
            step.result === "DENIED" ? tone("warning").base : failed ? tone("red").base : tone("green").base;

        return (
            <div className="relative" key={stepKey(step, index)}>
                <span
                    aria-hidden
                    className="absolute top-[15px] left-[13px] z-10 h-2 w-2 -translate-x-1/2 rounded-full"
                    style={{ background: dotColor }}
                />
                <div className="pl-5">
                    <Button
                        aria-pressed={active}
                        block
                        className={`!h-auto !min-w-0 !justify-start !rounded-xl !border !p-2.5 !text-left !transition-colors [&>span]:w-full ${compact ? "!text-[11px]" : ""}`}
                        onClick={() => select(step)}
                        style={{
                            background: active ? token.colorPrimaryBg : token.colorFillQuaternary,
                            borderColor: active ? token.colorPrimaryBorder : token.colorBorderSecondary,
                        }}
                        theme="default"
                        type="button"
                        variant="text"
                    >
                        <span className="min-w-0 flex-1">
                            <span className="flex min-w-0 items-center justify-between gap-2">
                                <span className="truncate font-medium" style={{ color: token.colorText }}>
                                    {operationLabel(step.operation)}
                                </span>
                                <ResultTag result={step.result} />
                            </span>
                            <span
                                className="mt-0.5 block truncate font-mono text-[10px]"
                                style={{ color: token.colorTextSecondary }}
                                title={path}
                            >
                                {path}
                            </span>
                            <span
                                className="mt-0.5 flex items-center justify-between gap-2 text-[10px] tabular-nums"
                                style={{ color: token.colorTextTertiary }}
                            >
                                <span>{formatDateTime(step.timestamp)}</span>
                                <span>{formatDuration(step.durationMs)}</span>
                            </span>
                        </span>
                    </Button>
                </div>
            </div>
        );
    };
    const renderActions = (items: ReplayStep[], key: string, compact = false) => {
        const selectedPosition = items.findIndex((step) => step.auditId === selectedAuditId);
        const requested = actionLimits[key] ?? ACTION_PREVIEW_LIMIT;
        const limit = Math.min(items.length, Math.max(requested, selectedPosition + 1));
        const visible = items.slice(0, limit);
        const remaining = items.length - visible.length;

        return (
            <div className="space-y-1.5">
                {visible.map((step, index) => actionRow(step, index, compact))}
                {remaining > 0 ? (
                    <Button block onClick={() => expandActions(key, items.length)} size="small" variant="text">
                        再显示 {formatCount(Math.min(ACTION_EXPANSION_SIZE, remaining))} 个操作，剩余{" "}
                        {formatCount(remaining)} 个
                    </Button>
                ) : null}
            </div>
        );
    };

    return (
        <div>
            <div
                className="rounded-2xl border p-4"
                style={{
                    background: token.colorFillQuaternary,
                    borderColor: token.colorBorderSecondary,
                }}
            >
                <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_auto_minmax(0,0.9fr)_auto_minmax(0,1fr)] lg:items-stretch">
                    <div className="min-w-0 rounded-xl p-3" style={{ background: token.colorBgContainer }}>
                        <LoginIcon aria-label="入口" size={15} style={{ color: token.colorTextTertiary }} />
                        <div className="mt-2 font-mono text-xs leading-5 break-all" style={{ color: token.colorText }}>
                            {entryPath}
                        </div>
                    </div>
                    <div
                        className="hidden items-center justify-center lg:flex"
                        style={{ color: token.colorTextTertiary }}
                    >
                        <ArrowRightIcon size={18} />
                    </div>
                    <div className="min-w-0 rounded-xl p-3" style={{ background: token.colorBgContainer }}>
                        <MapRoutePlanningIcon aria-label="过程" size={15} style={{ color: token.colorTextTertiary }} />
                        <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
                            <Tag theme="primary" variant="light">
                                {formatCount(pageCount)} 页
                            </Tag>
                            <Tag variant="light">{formatCount(actionCount)} 操作</Tag>
                            <Tag variant="light">{formatCount(recordCount)} 记录</Tag>
                        </div>
                        {pageNodes.length > 0 ? (
                            <div className="mt-2 font-mono text-[11px]" style={{ color: token.colorTextSecondary }}>
                                {pageSequence}
                                {hiddenPageCount > 0 ? ` +${hiddenPageCount}` : ""}
                            </div>
                        ) : null}
                    </div>
                    <div
                        className="hidden items-center justify-center lg:flex"
                        style={{ color: token.colorTextTertiary }}
                    >
                        <ArrowRightIcon size={18} />
                    </div>
                    <div className="min-w-0 rounded-xl p-3" style={{ background: token.colorBgContainer }}>
                        <LogoutIcon aria-label="出口" size={15} style={{ color: token.colorTextTertiary }} />
                        <div className="mt-2 font-mono text-xs leading-5 break-all" style={{ color: token.colorText }}>
                            {exitPath}
                        </div>
                    </div>
                </div>
                <div
                    className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-2 text-[11px]"
                    style={{ color: token.colorTextSecondary }}
                >
                    <span className="inline-flex items-center gap-1.5">
                        <FlagIcon size={13} />
                        {formatDateTime(session?.start ?? sorted[0].timestamp)}
                    </span>
                    <span className="inline-flex items-center gap-1.5">
                        <TimeIcon size={13} />
                        {formatDuration(
                            session?.durationMs ??
                                Math.max(0, timestampOf(sorted[sorted.length - 1].timestamp) - startOf(sorted[0])),
                        )}
                    </span>
                    <ResultTag result={status} />
                    {session?.archived ? (
                        <Tag theme="warning" variant="light">
                            含归档
                        </Tag>
                    ) : null}
                </div>
            </div>

            <div className="mt-4 flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                <div className="flex flex-wrap gap-x-5 gap-y-2 text-[11px]" style={{ color: token.colorTextSecondary }}>
                    <span className="inline-flex items-center gap-1.5">
                        <GitCommitIcon size={13} />
                        步骤 <strong style={{ color: token.colorText }}>{formatCount(sorted.length)}</strong>
                    </span>
                    <span className="inline-flex items-center gap-1.5">
                        <MapRoutePlanningIcon size={13} />
                        页面 <strong style={{ color: token.colorText }}>{formatCount(pageCount)}</strong>
                    </span>
                    <span className="inline-flex items-center gap-1.5">
                        <ErrorTriangleIcon size={13} />
                        异常 <strong style={{ color: token.colorText }}>{formatCount(anomalyCount)}</strong>
                    </span>
                    <span className="inline-flex items-center gap-1.5">
                        <FolderZipIcon size={13} />
                        归档步骤 <strong style={{ color: token.colorText }}>{formatCount(archivedCount)}</strong>
                    </span>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                    <Tag theme="primary" variant="light">
                        步骤 {selectedIndex + 1} / {sorted.length}
                    </Tag>
                    <Button
                        disabled={!previous}
                        icon={<ArrowLeftIcon size={13} />}
                        onClick={() => previous && select(previous)}
                        size="small"
                    >
                        上一步
                    </Button>
                    <Button
                        disabled={!next}
                        onClick={() => next && select(next)}
                        size="small"
                        suffix={<ArrowRightIcon size={13} />}
                    >
                        下一步
                    </Button>
                    {pageNodes.length > 0 ? (
                        <>
                            <Tag variant="light">
                                页面 {selectedPageIndex >= 0 ? selectedPageIndex + 1 : "—"} / {pageNodes.length}
                            </Tag>
                            <Button
                                disabled={!previousPage}
                                onClick={() => previousPage && select(previousPage.step)}
                                size="small"
                            >
                                上一页
                            </Button>
                            <Button
                                disabled={!nextPage}
                                onClick={() => nextPage && select(nextPage.step)}
                                size="small"
                                suffix={<ArrowRightIcon size={13} />}
                            >
                                下一页
                            </Button>
                        </>
                    ) : null}
                </div>
            </div>

            <div className="mt-4 grid gap-4 xl:grid-cols-[minmax(0,0.94fr)_minmax(0,1.06fr)]">
                <ScrollArea className="max-h-[640px] pr-1">
                    {prelude.length > 0 ? <div className="mb-3">{renderActions(prelude, "prelude", true)}</div> : null}
                    {pageNodes.length === 0 ? (
                        <div>{renderActions(sorted, "session")}</div>
                    ) : (
                        <div className="space-y-2">
                            {pageNodes.map((node, nodeIndex) => {
                                const active = node.step.auditId === selected.auditId;
                                const containsSelected = node.actions.some((step) => step.auditId === selected.auditId);
                                const denied = node.step.result === "DENIED";
                                const failed = node.step.result === "FAILED" || node.step.result === "FAILURE";

                                return (
                                    <div className="relative" key={stepKey(node.step, node.index)}>
                                        <div
                                            className="absolute top-6 left-0 z-10 h-2.5 w-2.5 -translate-x-1/2 rounded-full border-2"
                                            style={{
                                                background: denied
                                                    ? tone("warning").base
                                                    : failed
                                                      ? tone("red").base
                                                      : tone("green").base,
                                                borderColor: token.colorBgContainer,
                                                left: 13,
                                            }}
                                        />
                                        <div className="pl-5">
                                            <Button
                                                aria-pressed={active}
                                                block
                                                className="!h-auto !rounded-2xl !border !p-3 !text-left !transition-colors [&>span]:w-full [&>span]:flex-col [&>span]:items-stretch"
                                                onClick={() => select(node.step)}
                                                style={{
                                                    background:
                                                        active || containsSelected
                                                            ? token.colorPrimaryBg
                                                            : denied
                                                              ? token.colorWarningBg
                                                              : failed
                                                                ? token.colorErrorBg
                                                                : token.colorFillQuaternary,
                                                    borderColor:
                                                        active || containsSelected
                                                            ? token.colorPrimaryBorder
                                                            : denied
                                                              ? token.colorWarningBorder
                                                              : failed
                                                                ? token.colorErrorBorder
                                                                : token.colorBorderSecondary,
                                                }}
                                                theme="default"
                                                type="button"
                                                variant="text"
                                            >
                                                <div className="flex flex-wrap items-center gap-1.5">
                                                    {node.entry ? (
                                                        <Tag
                                                            icon={<LoginIcon size={11} />}
                                                            theme="primary"
                                                            variant="light"
                                                        >
                                                            入口
                                                        </Tag>
                                                    ) : null}
                                                    <Tag variant="light">第 {nodeIndex + 1} 站</Tag>
                                                    {node.exit ? (
                                                        <Tag icon={<LogoutIcon size={11} />} variant="light">
                                                            出口
                                                        </Tag>
                                                    ) : null}
                                                    {node.step.archived ? (
                                                        <Tag theme="warning" variant="light">
                                                            已归档
                                                        </Tag>
                                                    ) : null}
                                                    <span className="ml-auto">
                                                        <ResultTag result={node.step.result} />
                                                    </span>
                                                </div>
                                                <div
                                                    className="mt-2 font-mono text-xs leading-5 break-all"
                                                    style={{ color: token.colorText }}
                                                >
                                                    {pathOf(node.step)}
                                                </div>
                                                <div
                                                    className="mt-1.5 flex flex-wrap items-center justify-between gap-2 text-[10px] tabular-nums"
                                                    style={{ color: token.colorTextTertiary }}
                                                >
                                                    <span>{formatDateTime(node.step.timestamp)}</span>
                                                    <span>
                                                        {formatDuration(node.step.durationMs)} · {node.actions.length}{" "}
                                                        个操作
                                                    </span>
                                                </div>
                                            </Button>
                                        </div>
                                        {node.actions.length > 0 ? (
                                            <div className="mt-1.5">
                                                {renderActions(
                                                    node.actions,
                                                    `page:${stepKey(node.step, node.index)}`,
                                                    true,
                                                )}
                                            </div>
                                        ) : null}
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </ScrollArea>

                <ScrollArea
                    className="max-h-[640px] rounded-2xl border p-4"
                    style={{
                        background: selectedDenied
                            ? token.colorWarningBg
                            : selectedFailed
                              ? token.colorErrorBg
                              : token.colorFillQuaternary,
                        borderColor: selectedDenied
                            ? token.colorWarningBorder
                            : selectedFailed
                              ? token.colorErrorBorder
                              : token.colorBorderSecondary,
                    }}
                >
                    <div className="flex flex-wrap items-start justify-between gap-2">
                        <div className="flex flex-wrap items-center gap-2">
                            <Tag theme="primary" variant="light">
                                {selectedIsPage
                                    ? selectedPage?.entry
                                        ? "入口页面"
                                        : selectedPage?.exit
                                          ? "出口页面"
                                          : `页面 ${selectedPage ? pageNodes.indexOf(selectedPage) + 1 : ""}`
                                    : "业务操作"}
                            </Tag>
                            <ResultTag result={selected.result} />
                            {selected.archived ? (
                                <Tag theme="warning" variant="light">
                                    已归档
                                </Tag>
                            ) : null}
                        </div>
                        <span className="text-[11px]" style={{ color: token.colorTextTertiary }}>
                            {formatDateTime(selected.timestamp)} · {formatDuration(selected.durationMs)}
                        </span>
                    </div>
                    <div className="mt-3">
                        <div className="text-base font-semibold break-words" style={{ color: token.colorText }}>
                            {stepTitle(selected)}
                        </div>
                        <div
                            className="mt-1 font-mono text-[11px] break-all"
                            style={{ color: token.colorTextSecondary }}
                        >
                            {[selected.requestMethod, formatPath(selected.requestPath)].filter(Boolean).join(" ") ||
                                "—"}
                        </div>
                    </div>

                    <DescriptionGrid
                        className="mt-4"
                        columns={2}
                        items={[
                            {
                                label: "用户",
                                value: (
                                    <span className="flex min-w-0 flex-col">
                                        <span className="truncate text-sm">{session?.username ?? "未命名用户"}</span>
                                        <CopyableIdentifier className="mt-0.5 text-[11px]" mask value={selected.uid} />
                                    </span>
                                ),
                            },
                            {
                                label: "会话位置",
                                value: `${selectedIndex + 1} / ${sorted.length}`,
                            },
                            {
                                label: selectedIsPage ? "页面地址" : selectedPage ? "所属页面" : "页面线索",
                                value: (
                                    <span className="block truncate font-mono text-[11px]">
                                        {pathOf(selectedPage?.step ?? selected)}
                                    </span>
                                ),
                            },
                            {
                                label: "请求链路",
                                value: <CopyableIdentifier className="text-[11px]" mask value={selected.traceFlowId} />,
                            },
                            {
                                label: "审计编号",
                                value: <CopyableIdentifier className="text-[11px]" mask value={selected.auditId} />,
                            },
                            {
                                label: "会话结果",
                                value: <ResultTag result={status} />,
                            },
                        ]}
                    />

                    <div className="mt-4 border-t pt-3" style={{ borderColor: token.colorBorderSecondary }}>
                        <div
                            className="flex items-center gap-1.5 text-[11px] font-medium"
                            style={{ color: token.colorTextSecondary }}
                        >
                            <LinkIcon size={13} />
                            同一请求关联
                        </div>
                        <div className="mt-2 flex flex-wrap items-center gap-2">
                            {parent ? (
                                <Button onClick={() => select(parent)} size="small" variant="text">
                                    上一步 · {operationLabel(parent.operation)}
                                </Button>
                            ) : null}
                            {visibleChildren.map((child, index) => (
                                <Button
                                    key={stepKey(child, index)}
                                    onClick={() => select(child)}
                                    size="small"
                                    variant="text"
                                >
                                    下一步 · {operationLabel(child.operation)}
                                </Button>
                            ))}
                            {children.length > visibleChildren.length ? (
                                <span className="text-[11px]" style={{ color: token.colorTextTertiary }}>
                                    另有 {children.length - visibleChildren.length} 个下游步骤
                                </span>
                            ) : null}
                        </div>
                    </div>

                    <div className="mt-4 border-t pt-3" style={{ borderColor: token.colorBorderSecondary }}>
                        <div className="flex items-center gap-3">
                            <div
                                className="h-2.5 flex-1 overflow-hidden rounded-full"
                                style={{ background: token.colorFillSecondary }}
                            >
                                <div
                                    className="h-full rounded-full"
                                    style={{
                                        background: selectedColor,
                                        width: `${((selectedIndex + 1) / sorted.length) * 100}%`,
                                    }}
                                />
                            </div>
                            <span className="text-[11px] tabular-nums" style={{ color: token.colorTextTertiary }}>
                                {selectedIndex + 1} / {sorted.length}
                            </span>
                        </div>
                    </div>

                    {selected.actionSnapshot ? (
                        <div className="mt-4 border-t pt-3" style={{ borderColor: token.colorBorderSecondary }}>
                            <div className="text-[11px] font-medium" style={{ color: token.colorTextSecondary }}>
                                动作快照
                            </div>
                            <pre
                                className="mt-2 max-h-72 overflow-auto rounded-xl p-3 text-[11px] leading-5"
                                style={{
                                    background: token.colorBgContainer,
                                    color: token.colorTextSecondary,
                                }}
                            >
                                {prettySnapshot(selected.actionSnapshot)}
                            </pre>
                        </div>
                    ) : null}
                </ScrollArea>
            </div>
        </div>
    );
}

"use client";

import { type CSSProperties, useId, useState } from "react";

import { Button, Popup, Progress } from "tdesign-react";

import styles from "../styles/index-quota-indicator.module.css";

type QuotaLimit = {
    remainingPercent: number;
    resetLabel: string;
};

type QuotaSnapshot = {
    fiveHour: QuotaLimit;
    mcp: QuotaLimit;
    toolCalls: QuotaLimit;
    weekly: QuotaLimit;
};

type IndexQuotaIndicatorProps = {
    snapshot?: QuotaSnapshot;
};

const QUOTA_LIMITS = [
    { key: "fiveHour", label: "5 小时" },
    { key: "weekly", label: "每周" },
    { key: "toolCalls", label: "工具调用" },
] as const;

const RING_SIZE = 24;
const RING_CENTER = RING_SIZE / 2;
const RING_STROKE = 2.75;
const RING_RADIUS = RING_CENTER - RING_STROKE;

function clampPercent(value: number): number {
    return Math.min(100, Math.max(0, value));
}

function quotaHue(value: number): number {
    return Math.round(clampPercent(value) * 1.2);
}

function quotaColor(value: number): string {
    return `hsl(${quotaHue(value)} 72% 42%)`;
}

function QuotaMeter({ label, limit }: { label: string; limit?: QuotaLimit }) {
    const percentage = limit ? clampPercent(limit.remainingPercent) : 0;

    return (
        <div className={styles.meter}>
            <div className={styles.meterHeader}>
                <span className={styles.meterLabel}>{label}</span>
                <span className={`${styles.meterValue} ${limit ? "" : styles.meterValuePending}`}>
                    {limit ? `${Math.round(percentage)}%` : "--"}
                </span>
            </div>
            <Progress
                className={styles.meterProgress}
                color={limit ? quotaColor(percentage) : undefined}
                label={false}
                percentage={percentage}
                strokeWidth={5}
                theme="line"
                trackColor="var(--quota-track)"
            />
            <span className={styles.meterReset}>{limit ? `重置于 ${limit.resetLabel}` : "待接入"}</span>
        </div>
    );
}

export function IndexQuotaIndicator({ snapshot }: IndexQuotaIndicatorProps) {
    const [open, setOpen] = useState(false);
    const panelId = useId();
    const limitedWindows = snapshot ? QUOTA_LIMITS.map(({ key }) => snapshot[key]) : [];
    const remainingPercent = snapshot
        ? Math.min(...limitedWindows.map((limit) => clampPercent(limit.remainingPercent)))
        : 100;
    const ringStyle = { "--quota-hue": quotaHue(remainingPercent) } as CSSProperties;
    const triggerLabel = snapshot === undefined ? "剩余额度，数据待接入" : `剩余额度，${Math.round(remainingPercent)}%`;

    const panel = (
        <div aria-label="剩余额度详情" className={styles.panel} id={panelId} role="dialog">
            <div className={styles.header}>
                <span className={styles.title}>剩余额度</span>
                <span className={styles.status}>
                    <span aria-hidden className={styles.statusDot} />
                    {snapshot ? "实时额度" : "待接入"}
                </span>
            </div>

            <div className={styles.limits}>
                {QUOTA_LIMITS.map(({ key, label }) => (
                    <QuotaMeter key={key} label={label} limit={snapshot?.[key]} />
                ))}
            </div>

            <div aria-hidden className={styles.divider} />

            <div className={styles.mcp}>
                <div className={styles.mcpHeader}>
                    <span className={styles.mcpLabel}>DANS MCP</span>
                    <span className={`${styles.meterValue} ${snapshot ? "" : styles.meterValuePending}`}>
                        {snapshot ? `${Math.round(clampPercent(snapshot.mcp.remainingPercent))}%` : "--"}
                    </span>
                </div>
                <Progress
                    className={styles.meterProgress}
                    color={snapshot ? quotaColor(snapshot.mcp.remainingPercent) : undefined}
                    label={false}
                    percentage={snapshot ? clampPercent(snapshot.mcp.remainingPercent) : 0}
                    strokeWidth={5}
                    theme="line"
                    trackColor="var(--quota-track)"
                />
                <span className={styles.meterReset}>{snapshot ? `重置于 ${snapshot.mcp.resetLabel}` : "待接入"}</span>
            </div>
        </div>
    );

    return (
        <Popup
            content={panel}
            destroyOnClose
            onVisibleChange={setOpen}
            overlayClassName={styles.popup}
            placement="bottom-right"
            showArrow={false}
            trigger="hover"
            visible={open}
        >
            <Button
                aria-controls={panelId}
                aria-expanded={open}
                aria-haspopup="dialog"
                aria-label={triggerLabel}
                className={`${styles.trigger} ${open ? styles.triggerOpen : ""}`}
                onBlur={() => setOpen(false)}
                onFocus={() => setOpen(true)}
                shape="square"
                type="button"
                variant="text"
            >
                <svg
                    aria-hidden
                    className={styles.ring}
                    height={RING_SIZE}
                    viewBox={`0 0 ${RING_SIZE} ${RING_SIZE}`}
                    width={RING_SIZE}
                >
                    <circle
                        className={styles.ringTrack}
                        cx={RING_CENTER}
                        cy={RING_CENTER}
                        fill="none"
                        r={RING_RADIUS}
                        strokeWidth={RING_STROKE}
                    />
                    <circle
                        className={styles.ringValue}
                        cx={RING_CENTER}
                        cy={RING_CENTER}
                        fill="none"
                        pathLength={100}
                        r={RING_RADIUS}
                        strokeDasharray={100}
                        strokeDashoffset={100 - clampPercent(remainingPercent)}
                        strokeWidth={RING_STROKE}
                        style={ringStyle}
                        transform={`rotate(-90 ${RING_CENTER} ${RING_CENTER})`}
                    />
                </svg>
            </Button>
        </Popup>
    );
}

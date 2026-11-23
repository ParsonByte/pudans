"use client";

import type { RiskSceneStat } from "@pudans-dev/api-client";
import { Empty, Tag, Tooltip } from "tdesign-react";

import { formatCount } from "../../../../composables/_format";
import { useDashboardTheme } from "../../../overview/dashboard/composables/_theme";
import { RISK_ACTION_LABELS, riskActionLabel } from "../composables/_risk";

type RiskSceneDistributionProps = {
    scenes?: RiskSceneStat[];
};

const SCENE_LIMIT = 6;

function actionTheme(action?: string): "danger" | "warning" | "default" {
    return action === "BLOCK" ? "danger" : action === "ALERT" ? "warning" : "default";
}

export function RiskSceneDistribution({ scenes }: RiskSceneDistributionProps) {
    const { token, tone } = useDashboardTheme();
    const data = [...(scenes ?? [])]
        .sort((left, right) => (right.events ?? 0) - (left.events ?? 0))
        .slice(0, SCENE_LIMIT);

    if (data.length === 0) {
        return <Empty description="暂无风险场景" size="small" />;
    }

    const maximum = Math.max(1, ...data.map((scene) => scene.events ?? 0));
    const blockedColor = tone("red").base;
    const alertColor = tone("warning").base;
    const neutralColor = tone("blue").base;

    return (
        <div>
            <div className="space-y-4">
                {data.map((scene) => {
                    const events = scene.events ?? 0;
                    const blocked = scene.blocked ?? 0;
                    const alerts = scene.alerts ?? Math.max(0, events - blocked);
                    const other = Math.max(0, events - blocked - alerts);
                    const width = events === 0 ? 0 : Math.max(3, (events / maximum) * 100);

                    return (
                        <div key={scene.scene}>
                            <div className="flex items-center gap-3">
                                <Tooltip content={scene.description || scene.scene}>
                                    <span
                                        className="min-w-0 flex-1 truncate text-sm font-medium"
                                        style={{ color: token.colorText }}
                                    >
                                        {scene.label || scene.description || scene.scene}
                                    </span>
                                </Tooltip>
                                <Tag theme={actionTheme(scene.action)} variant="light">
                                    {scene.action ? riskActionLabel(scene.action) : "未配置"}
                                </Tag>
                                <span className="text-xs font-semibold tabular-nums" style={{ color: token.colorText }}>
                                    {formatCount(events)}
                                </span>
                            </div>
                            <div
                                className="mt-2 h-2.5 overflow-hidden rounded-full"
                                style={{ background: token.colorFillSecondary }}
                            >
                                <div className="flex h-full" style={{ width: `${width}%` }}>
                                    {blocked > 0 ? (
                                        <span
                                            style={{
                                                background: blockedColor,
                                                width: `${(blocked / events) * 100}%`,
                                            }}
                                        />
                                    ) : null}
                                    {alerts > 0 ? (
                                        <span
                                            style={{
                                                background: alertColor,
                                                width: `${(alerts / events) * 100}%`,
                                            }}
                                        />
                                    ) : null}
                                    {other > 0 ? (
                                        <span
                                            style={{
                                                background: neutralColor,
                                                width: `${(other / events) * 100}%`,
                                            }}
                                        />
                                    ) : null}
                                </div>
                            </div>
                            <div
                                className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px]"
                                style={{ color: token.colorTextTertiary }}
                            >
                                <span>阻断 {formatCount(blocked)}</span>
                                <span>告警 {formatCount(alerts)}</span>
                                <span>主体 {formatCount(scene.subjects)}</span>
                            </div>
                        </div>
                    );
                })}
            </div>
            <div
                className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-2 border-t pt-3 text-[11px]"
                style={{ borderColor: token.colorBorderSecondary, color: token.colorTextTertiary }}
            >
                <span className="flex items-center gap-1.5">
                    <span className="h-2 w-2 rounded-full" style={{ background: blockedColor }} />
                    {RISK_ACTION_LABELS.BLOCK}
                </span>
                <span className="flex items-center gap-1.5">
                    <span className="h-2 w-2 rounded-full" style={{ background: alertColor }} />
                    {RISK_ACTION_LABELS.ALERT}
                </span>
            </div>
        </div>
    );
}

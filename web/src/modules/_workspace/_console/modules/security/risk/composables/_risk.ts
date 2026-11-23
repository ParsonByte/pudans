import type { RiskRule, RiskSceneStat } from "@pudans-dev/api-client";

export type RiskRuleSpec = {
    action?: string;
    blockSeconds?: number;
    description?: string;
    enabled?: boolean;
    metric?: string;
    scene?: string;
    threshold?: number;
    windowSeconds?: number;
};

export type RiskFormValues = {
    action?: string;
    blockSeconds?: number;
    description?: string;
    enabled?: boolean;
    metric?: string;
    name: string;
    scene: string;
    threshold?: number;
    windowSeconds?: number;
};

export const RISK_ACTION_OPTIONS = [
    { label: "阻断", value: "BLOCK" },
    { label: "告警", value: "ALERT" },
];

export const RISK_ACTION_LABELS: Record<string, string> = {
    ALERT: "告警",
    BLOCK: "阻断",
};

export function readRiskSpec(rule: RiskRule): RiskRuleSpec {
    return rule.spec as unknown as RiskRuleSpec;
}

export function riskActionLabel(action?: string): string {
    return action ? (RISK_ACTION_LABELS[action] ?? action) : "未配置";
}

export function sceneLabel(scene?: string, scenes?: RiskSceneStat[]): string {
    if (!scene) {
        return "未知场景";
    }

    const stat = scenes?.find((item) => item.scene === scene);

    return stat?.label || stat?.description || scene;
}

export function ruleThreshold(rule: RiskRule): string {
    const spec = readRiskSpec(rule);

    return `${spec.threshold ?? "—"} / ${spec.windowSeconds ?? "—"}s`;
}

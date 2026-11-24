"use client";

import type { RiskRule, RiskSceneStat } from "@pudans-dev/api-client";
import {
    BrowseIcon,
    DeleteIcon,
    EditIcon,
    MoreIcon,
    NotificationIcon,
    ShieldErrorIcon,
    TaskCheckedIcon,
} from "tdesign-icons-react";
import { Button, Card, Dropdown, Tag, Tooltip } from "tdesign-react";
import type { DropdownOption } from "tdesign-react";

import { formatDateTime } from "../../../../composables/_format";
import { useDashboardTheme } from "../../../overview/dashboard/composables/_theme";
import { readRiskSpec, riskActionLabel, ruleThreshold, sceneLabel } from "../composables/_risk";

type RiskRuleCardProps = {
    onDelete: () => void;
    onDetails: () => void;
    onEdit: () => void;
    pending: boolean;
    rule: RiskRule;
    scenes?: RiskSceneStat[];
};

export function RiskRuleCard({ onDelete, onDetails, onEdit, pending, rule, scenes }: RiskRuleCardProps) {
    const { token, tone } = useDashboardTheme();
    const spec = readRiskSpec(rule);
    const blocked = spec.action === "BLOCK";
    const alerted = spec.action === "ALERT";
    const actionColors = tone(blocked ? "red" : alerted ? "warning" : "blue");
    const RuleIcon = blocked ? ShieldErrorIcon : alerted ? NotificationIcon : TaskCheckedIcon;
    const description = spec.description?.trim();
    const actions: DropdownOption[] = [
        { content: "查看详情", prefixIcon: <BrowseIcon size={16} />, value: "details" },
        { content: "编辑规则", prefixIcon: <EditIcon size={16} />, value: "edit" },
        {
            content: "删除",
            prefixIcon: <DeleteIcon size={16} />,
            theme: "error",
            value: "delete",
        },
    ];

    return (
        <Card
            actions={
                <Tag theme={spec.enabled === false ? "default" : "success"} variant="light">
                    {spec.enabled === false ? "已停用" : "已启用"}
                </Tag>
            }
            avatar={
                <span
                    className="flex size-14 shrink-0 items-center justify-center rounded-2xl"
                    style={{ background: actionColors.soft, color: actionColors.base }}
                >
                    <RuleIcon size={28} />
                </span>
            }
            bordered={false}
            bodyClassName="!flex flex-1 flex-col"
            className="!flex h-full min-w-0 flex-col"
            footer={
                <div className="flex w-full min-w-0 items-center justify-between gap-3">
                    <span className="truncate text-xs" style={{ color: token.colorTextTertiary }}>
                        创建于 {formatDateTime(rule.metadata.creationTimestamp)}
                    </span>
                    <Dropdown
                        disabled={pending}
                        onClick={(item) => {
                            if (item.value === "details") {
                                onDetails();
                            }
                            if (item.value === "edit") {
                                onEdit();
                            }
                            if (item.value === "delete") {
                                onDelete();
                            }
                        }}
                        options={actions}
                        placement="bottom-right"
                        trigger="click"
                    >
                        <Button
                            aria-label={`${rule.metadata.name} 操作`}
                            icon={<MoreIcon />}
                            shape="square"
                            variant="text"
                        />
                    </Dropdown>
                </div>
            }
            headerClassName="!items-start"
        >
            <div className="min-w-0">
                <div className="truncate text-base font-medium" title={rule.metadata.name}>
                    {rule.metadata.name}
                </div>
                <p
                    className="mt-2 line-clamp-2 min-h-10 text-xs leading-5"
                    style={{ color: token.colorTextSecondary }}
                    title={description}
                >
                    {description || "未填写描述"}
                </p>
            </div>
            <div className="mt-auto flex flex-wrap items-center gap-x-2 gap-y-2 pt-4 text-xs">
                <Tag theme={blocked ? "danger" : alerted ? "warning" : "default"} variant="light">
                    {riskActionLabel(spec.action)}
                </Tag>
                <Tooltip content={spec.scene}>
                    <span className="min-w-0 truncate" style={{ color: token.colorTextSecondary }}>
                        {sceneLabel(spec.scene, scenes)}
                    </span>
                </Tooltip>
                <span aria-hidden="true" style={{ color: token.colorTextTertiary }}>
                    ·
                </span>
                <span className="shrink-0" style={{ color: token.colorTextSecondary }}>
                    阈值 {ruleThreshold(rule)}
                </span>
            </div>
        </Card>
    );
}

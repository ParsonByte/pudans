"use client";

import type { RiskRule, RiskSceneStat } from "@pudans-dev/api-client";
import { AddIcon, RefreshIcon } from "tdesign-icons-react";
import { Button, Card, Empty, Pagination, Skeleton } from "tdesign-react";
import type { PaginationProps } from "tdesign-react";

import { ScrollArea } from "@/components/ui";

import { consoleErrorMessage } from "../../../../composables/_errors";
import { RiskRuleCard } from "./risk-rule-card";

type RiskRuleGridProps = {
    error?: unknown;
    loading: boolean;
    onCreate: () => void;
    onDelete: (rule: RiskRule) => void;
    onDetails: (rule: RiskRule) => void;
    onEdit: (rule: RiskRule) => void;
    onRetry: () => void;
    pagination: PaginationProps;
    pending: boolean;
    rules?: RiskRule[];
    scenes?: RiskSceneStat[];
    total: number;
};

const RULE_SKELETON_COUNT = 3;

export function RiskRuleGrid({
    error,
    loading,
    onCreate,
    onDelete,
    onDetails,
    onEdit,
    onRetry,
    pagination,
    pending,
    rules,
    scenes,
    total,
}: RiskRuleGridProps) {
    if (error) {
        return (
            <Card bordered={false}>
                <Empty
                    action={
                        <Button icon={<RefreshIcon />} onClick={onRetry} theme="primary">
                            重试
                        </Button>
                    }
                    description={consoleErrorMessage(error)}
                    type="network-error"
                />
            </Card>
        );
    }

    if (loading) {
        return (
            <div className="grid min-w-0 grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
                {Array.from({ length: RULE_SKELETON_COUNT }).map((_, index) => (
                    <Card bordered={false} key={index}>
                        <Skeleton animation="gradient" theme="avatar-text" />
                    </Card>
                ))}
            </div>
        );
    }

    if (!rules?.length) {
        return (
            <Card bordered={false}>
                <Empty
                    action={
                        <Button icon={<AddIcon />} onClick={onCreate} theme="primary">
                            新建风险规则
                        </Button>
                    }
                    description="尚未配置风险规则"
                />
            </Card>
        );
    }

    return (
        <div className="flex min-w-0 flex-col gap-4">
            <div className="grid min-w-0 grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
                {rules.map((rule) => (
                    <RiskRuleCard
                        key={rule.metadata.name}
                        onDelete={() => onDelete(rule)}
                        onDetails={() => onDetails(rule)}
                        onEdit={() => onEdit(rule)}
                        pending={pending}
                        rule={rule}
                        scenes={scenes}
                    />
                ))}
            </div>
            {total > 0 ? (
                <ScrollArea axis="x" className="flex min-w-0 justify-end">
                    <Pagination {...pagination} total={total} />
                </ScrollArea>
            ) : null}
        </div>
    );
}

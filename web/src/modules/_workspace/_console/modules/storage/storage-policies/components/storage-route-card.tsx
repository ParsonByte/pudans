"use client";

import { FolderOpenIcon, HistoryIcon } from "tdesign-icons-react";
import { Button, Card, Tag } from "tdesign-react";

import { useDesignTokens } from "@/components/ui";

import { CopyableIdentifier } from "../../../../components/copyable-identifier";
import type { StoragePolicy, StorageRoute, StorageRoutePurposeDescriptor } from "../../../../composables/_storage";
import { storagePolicyDisplayName, storageRoutePurposeMeta } from "../composables/_storage-policies";

type StorageRouteCardProps = {
    descriptor: StorageRoutePurposeDescriptor;
    onDelete?: () => void;
    onEdit?: () => void;
    onTest?: () => void;
    pending: boolean;
    policies?: StoragePolicy[];
    route?: StorageRoute;
};

export function StorageRouteCard({
    descriptor,
    onDelete,
    onEdit,
    onTest,
    pending,
    policies,
    route,
}: StorageRouteCardProps) {
    const token = useDesignTokens();
    const meta = storageRoutePurposeMeta(descriptor.purpose);
    const PurposeIcon = descriptor.purpose === "sandbox_snapshot" ? HistoryIcon : FolderOpenIcon;
    const policy = policies?.find((item) => item.metadata.name === route?.spec.policyName);
    const policyName = route?.spec.policyName ?? "";
    const enabled = route ? route.spec.enabled !== false : false;
    const ready = policy?.status?.ready;

    return (
        <Card
            bodyStyle={{ display: "flex", flex: 1, flexDirection: "column", minHeight: 0, padding: 20 }}
            bordered={false}
            className="h-full"
        >
            <div className="flex min-w-0 items-start gap-3">
                <span
                    className="flex size-10 shrink-0 items-center justify-center rounded-2xl"
                    style={{ background: token.colorPrimaryBg, color: token.colorPrimary }}
                >
                    <PurposeIcon size={20} />
                </span>
                <div className="min-w-0 flex-1">
                    <div className="text-base font-semibold">{meta.label}</div>
                    <div className="mt-1 flex flex-wrap items-center gap-1.5">
                        <Tag theme={route ? (enabled ? "success" : "warning") : "default"} variant="light">
                            {route ? (enabled ? "已绑定" : "已停用") : "未绑定"}
                        </Tag>
                        {descriptor.fallbackToDefaultPolicy ? (
                            <Tag theme="default" variant="light">
                                可回退默认策略
                            </Tag>
                        ) : null}
                    </div>
                </div>
            </div>

            <div className="mt-3 text-xs" style={{ color: token.colorTextTertiary }}>
                {meta.description}
            </div>

            {route ? (
                <div className="mt-4 rounded-lg p-3" style={{ background: token.colorFillTertiary }}>
                    <div className="flex flex-wrap items-center justify-between gap-2">
                        <div className="min-w-0">
                            <div className="text-[11px]" style={{ color: token.colorTextTertiary }}>
                                目标策略
                            </div>
                            <div className="mt-0.5 truncate text-sm font-medium">
                                {policy ? storagePolicyDisplayName(policy) : policyName || "未指定"}
                            </div>
                        </div>
                        {policy ? (
                            <Tag theme={ready ? "success" : "warning"} variant="light">
                                {ready ? "就绪" : "未就绪"}
                            </Tag>
                        ) : null}
                    </div>
                    {policyName ? (
                        <div className="mt-2 min-w-0">
                            <CopyableIdentifier
                                className="[&_button]:shrink-0"
                                display={<span className="min-w-0 truncate font-mono text-xs">{policyName}</span>}
                                value={policyName}
                            />
                        </div>
                    ) : null}
                    {route.spec.description?.trim() ? (
                        <div className="mt-2 text-xs" style={{ color: token.colorTextSecondary }}>
                            {route.spec.description.trim()}
                        </div>
                    ) : null}
                </div>
            ) : (
                <div
                    className="mt-4 rounded-lg border border-dashed p-3 text-xs"
                    style={{ borderColor: token.colorBorder, color: token.colorTextTertiary }}
                >
                    {descriptor.fallbackToDefaultPolicy
                        ? "尚未绑定存储策略，该用途将使用默认策略。"
                        : "尚未绑定存储策略，该用途在绑定前不可用。"}
                </div>
            )}

            {route && !enabled ? (
                <div className="mt-3 text-xs" style={{ color: token.colorWarning }}>
                    {descriptor.fallbackToDefaultPolicy
                        ? "路由已停用，该用途将回退到默认策略。"
                        : "路由已停用，该用途将不可用。"}
                </div>
            ) : null}

            <div
                className="mt-auto flex flex-wrap items-center gap-2 border-t pt-4"
                style={{ borderColor: token.colorBorderSecondary }}
            >
                {onEdit ? (
                    <Button disabled={pending} onClick={onEdit} size="small" variant="outline">
                        {route ? "更换策略" : "绑定策略"}
                    </Button>
                ) : null}
                {onTest && route && enabled ? (
                    <Button disabled={pending} onClick={onTest} size="small" variant="outline">
                        测试连接
                    </Button>
                ) : null}
                {onDelete && route ? (
                    <Button disabled={pending} onClick={onDelete} size="small" theme="danger" variant="text">
                        解除绑定
                    </Button>
                ) : null}
            </div>
        </Card>
    );
}

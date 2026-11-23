"use client";

import { DeleteIcon, EditIcon, LinkIcon, MoreIcon } from "tdesign-icons-react";
import { Button, Card, Dropdown, type DropdownOption, Switch, Tag, Tooltip } from "tdesign-react";

import { useDesignTokens } from "@/components/ui";

import type { IdentityProviderCard } from "../composables/_auth-providers";
import { ProviderIcon } from "./provider-icon";

type ProviderCardProps = {
    canManage: boolean;
    onDelete?: () => void;
    onEdit?: () => void;
    onToggle: (enabled: boolean) => void;
    pending: boolean;
    provider: IdentityProviderCard;
};

const PROVIDER_KIND_LABELS = {
    feishu: "飞书",
    oauth2: "OAuth2",
    oidc: "OIDC",
} as const;

export function ProviderCard({ canManage, onDelete, onEdit, onToggle, pending, provider }: ProviderCardProps) {
    const tokens = useDesignTokens();
    const actions: DropdownOption[] = [];
    const canToggle = canManage && provider.configured && !provider.environmentManaged;
    const endpointLabel = provider.endpoint?.includes("{baseUrl}") ? "服务端默认回调地址" : provider.endpoint;
    const kindLabel = PROVIDER_KIND_LABELS[provider.kind];

    if (onEdit) {
        actions.push({ content: "编辑配置", prefixIcon: <EditIcon />, value: "edit" });
    }
    if (onDelete) {
        actions.push({ content: "删除", prefixIcon: <DeleteIcon />, theme: "error", value: "delete" });
    }

    return (
        <Card className="h-full" hoverShadow>
            <div className="flex h-full min-w-0 flex-col">
                <div className="flex min-w-0 items-start justify-between gap-3">
                    <div className="flex min-w-0 items-start gap-3">
                        <ProviderIcon kind={provider.kind} logo={provider.logo} />
                        <div className="min-w-0">
                            <div className="truncate text-base font-semibold">{provider.displayName}</div>
                            <div className="mt-1 flex flex-wrap items-center gap-1.5">
                                {provider.kind !== "feishu" ? (
                                    <Tag theme="default" variant="light">
                                        {kindLabel}
                                    </Tag>
                                ) : null}
                                <ProviderStatus provider={provider} />
                            </div>
                        </div>
                    </div>
                    {canManage && actions.length > 0 ? (
                        <Dropdown
                            disabled={pending}
                            onClick={(item) => {
                                if (item.value === "edit") {
                                    onEdit?.();
                                }
                                if (item.value === "delete") {
                                    onDelete?.();
                                }
                            }}
                            options={actions}
                            placement="bottom-right"
                            trigger="click"
                        >
                            <Button
                                aria-label={`${provider.displayName} 操作`}
                                icon={<MoreIcon />}
                                shape="square"
                                variant="text"
                            />
                        </Dropdown>
                    ) : null}
                </div>

                <div
                    className="mt-3 flex min-w-0 items-center gap-2 text-xs"
                    style={{ color: tokens.colorTextTertiary }}
                >
                    <LinkIcon className="shrink-0" />
                    {endpointLabel ? (
                        <Tooltip content={provider.endpoint}>
                            <span className="truncate">{endpointLabel}</span>
                        </Tooltip>
                    ) : (
                        <span>{provider.environmentManaged ? "由运行环境配置" : "使用服务端默认回调地址"}</span>
                    )}
                </div>

                <div
                    className="mt-auto flex items-center justify-between gap-3 border-t pt-4"
                    style={{ borderColor: tokens.colorBorderSecondary }}
                >
                    <span className="text-sm" style={{ color: tokens.colorTextSecondary }}>
                        登录入口
                    </span>
                    <Tooltip
                        content={
                            provider.environmentManaged
                                ? "由运行环境配置"
                                : !provider.configured
                                  ? "请先补全认证配置"
                                  : undefined
                        }
                    >
                        <span>
                            <Switch
                                aria-label={`${provider.enabled ? "停用" : "启用"}${provider.displayName}`}
                                disabled={!canToggle || pending}
                                loading={pending}
                                onChange={onToggle}
                                size="small"
                                value={provider.enabled}
                            />
                        </span>
                    </Tooltip>
                </div>
            </div>
        </Card>
    );
}

function ProviderStatus({ provider }: { provider: IdentityProviderCard }) {
    if (!provider.configured) {
        return (
            <Tag theme="warning" variant="light">
                待完善
            </Tag>
        );
    }

    return (
        <Tag theme={provider.enabled ? "success" : "default"} variant="light">
            {provider.enabled ? "已启用" : "已停用"}
        </Tag>
    );
}

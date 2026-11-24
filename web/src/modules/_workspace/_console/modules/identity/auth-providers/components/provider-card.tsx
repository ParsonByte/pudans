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
        <Card
            actions={<ProviderStatus provider={provider} />}
            avatar={<ProviderIcon kind={provider.kind} logo={provider.logo} />}
            bordered={false}
            bodyClassName="!flex flex-1 flex-col"
            className="!flex h-full min-w-0 flex-col"
            footer={
                <div className="flex w-full min-w-0 items-center justify-between gap-3">
                    <Tooltip
                        content={
                            provider.environmentManaged
                                ? "由运行环境配置"
                                : !provider.configured
                                  ? "请先补全认证配置"
                                  : undefined
                        }
                    >
                        <span className="flex min-w-0 items-center gap-2">
                            <Switch
                                aria-label={`${provider.enabled ? "停用" : "启用"}${provider.displayName}`}
                                disabled={!canToggle || pending}
                                loading={pending}
                                onChange={onToggle}
                                size="small"
                                value={provider.enabled}
                            />
                            <span className="truncate text-xs" style={{ color: tokens.colorTextSecondary }}>
                                登录入口
                            </span>
                        </span>
                    </Tooltip>
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
            }
            headerClassName="!items-start"
        >
            <div className="min-w-0">
                <div className="truncate text-base font-medium" title={provider.displayName}>
                    {provider.displayName}
                </div>
                <p
                    className="mt-2 line-clamp-2 min-h-10 text-xs leading-5"
                    style={{ color: tokens.colorTextSecondary }}
                >
                    {provider.description}
                </p>
            </div>
            <div
                className="mt-auto flex min-w-0 items-center gap-1.5 pt-4 text-xs"
                style={{ color: tokens.colorTextTertiary }}
            >
                <span className="shrink-0">{kindLabel}</span>
                <span aria-hidden="true">·</span>
                <LinkIcon className="shrink-0" />
                {endpointLabel ? (
                    <Tooltip content={provider.endpoint}>
                        <span className="truncate">{endpointLabel}</span>
                    </Tooltip>
                ) : (
                    <span className="truncate">
                        {provider.environmentManaged ? "由运行环境配置" : "使用服务端默认回调地址"}
                    </span>
                )}
            </div>
        </Card>
    );
}

function ProviderStatus({ provider }: { provider: IdentityProviderCard }) {
    if (!provider.configured) {
        return <Tag theme="warning">待完善</Tag>;
    }

    return <Tag theme={provider.enabled ? "success" : "default"}>{provider.enabled ? "已启用" : "已停用"}</Tag>;
}

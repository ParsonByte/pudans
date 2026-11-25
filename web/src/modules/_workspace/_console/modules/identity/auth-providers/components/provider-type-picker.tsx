"use client";

import { CheckCircleIcon, ChevronRightIcon } from "tdesign-icons-react";
import { Button, Card } from "tdesign-react";

import { GlobalDialog, useDesignTokens } from "@/components/ui";

import type { IdentityProviderKind } from "../composables/_auth-providers";
import { ProviderKindIcon } from "./provider-icon";

type ProviderTypePickerProps = {
    feishuConfigured: boolean;
    onClose: () => void;
    onSelect: (kind: IdentityProviderKind) => void;
    open: boolean;
};

const PROVIDER_TYPES: {
    description: string;
    kind: IdentityProviderKind;
    title: string;
}[] = [
    { description: "标准 OAuth2 授权码登录", kind: "oauth2", title: "OAuth2" },
    { description: "OpenID Connect 身份认证", kind: "oidc", title: "OIDC" },
    { description: "使用飞书企业应用完成第三方登录", kind: "feishu", title: "飞书" },
];

export function ProviderTypePicker({ feishuConfigured, onClose, onSelect, open }: ProviderTypePickerProps) {
    const tokens = useDesignTokens();

    return (
        <GlobalDialog footer={false} onClose={onClose} open={open} title="添加身份提供商" width={560}>
            <div className="flex flex-col gap-3">
                {PROVIDER_TYPES.map((option) => {
                    const disabled = option.kind === "feishu" && feishuConfigured;

                    return (
                        <Card bordered={false} key={option.kind} size="small">
                            <div className="flex items-center gap-4">
                                <span
                                    className="flex size-11 shrink-0 items-center justify-center rounded-xl"
                                    style={{
                                        background: tokens.colorPrimaryBg,
                                        color: tokens.colorPrimary,
                                    }}
                                >
                                    <ProviderKindIcon kind={option.kind} size={22} />
                                </span>
                                <div className="flex min-w-0 flex-1 flex-col gap-1">
                                    <span className="font-medium text-[var(--td-text-color-primary)]">
                                        {option.title}
                                    </span>
                                    <span className="text-xs text-[var(--td-text-color-secondary)]">
                                        {option.description}
                                    </span>
                                </div>
                                {disabled ? (
                                    <span className="flex shrink-0 items-center gap-1 text-sm text-[var(--td-text-color-secondary)]">
                                        <CheckCircleIcon />
                                        已配置
                                    </span>
                                ) : (
                                    <Button
                                        onClick={() => onSelect(option.kind)}
                                        size="small"
                                        suffix={<ChevronRightIcon />}
                                        theme="primary"
                                        variant="text"
                                    >
                                        添加
                                    </Button>
                                )}
                            </div>
                        </Card>
                    );
                })}
            </div>
        </GlobalDialog>
    );
}

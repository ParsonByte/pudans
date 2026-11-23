"use client";

import { Button, Card, Tag } from "tdesign-react";

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
    kind: IdentityProviderKind;
    title: string;
}[] = [
    { kind: "oauth2", title: "OAuth2" },
    { kind: "oidc", title: "OIDC" },
    { kind: "feishu", title: "飞书" },
];

export function ProviderTypePicker({ feishuConfigured, onClose, onSelect, open }: ProviderTypePickerProps) {
    const tokens = useDesignTokens();

    return (
        <GlobalDialog footer={false} onClose={onClose} open={open} title="添加身份提供商" width={680}>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                {PROVIDER_TYPES.map((option) => {
                    const disabled = option.kind === "feishu" && feishuConfigured;

                    return (
                        <Card key={option.kind} title={option.title}>
                            <div className="flex items-end justify-between gap-3">
                                <span
                                    className="flex size-11 items-center justify-center rounded-xl"
                                    style={{
                                        background: tokens.colorFillTertiary,
                                        color: tokens.colorTextSecondary,
                                    }}
                                >
                                    <ProviderKindIcon kind={option.kind} size={22} />
                                </span>
                                {disabled ? (
                                    <Tag theme="default" variant="light">
                                        已配置
                                    </Tag>
                                ) : (
                                    <Button onClick={() => onSelect(option.kind)} size="small" theme="primary">
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

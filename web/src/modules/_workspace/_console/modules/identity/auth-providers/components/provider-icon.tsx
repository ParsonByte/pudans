"use client";

import { ChatBubbleIcon, LinkIcon, UserCircleIcon } from "tdesign-icons-react";
import { Avatar } from "tdesign-react";

import { useDesignTokens } from "@/components/ui";

import type { IdentityProviderKind } from "../composables/_auth-providers";

type ProviderIconProps = {
    kind: IdentityProviderKind;
    logo?: string;
    size?: number;
};

export function ProviderIcon({ kind, logo, size = 44 }: ProviderIconProps) {
    const tokens = useDesignTokens();

    if (logo) {
        return (
            <Avatar
                image={logo}
                shape="round"
                size={`${size}px`}
                style={{ border: `1px solid ${tokens.colorBorderSecondary}`, flexShrink: 0 }}
            />
        );
    }

    return (
        <span
            className="flex shrink-0 items-center justify-center rounded-xl"
            style={{
                background: tokens.colorFillTertiary,
                color: kind === "feishu" ? tokens.colorPrimary : tokens.colorTextSecondary,
                height: size,
                width: size,
            }}
        >
            <ProviderKindIcon kind={kind} size={size * 0.48} />
        </span>
    );
}

export function ProviderKindIcon({ kind, size = 20 }: { kind: IdentityProviderKind; size?: number }) {
    const iconStyle = { height: size, width: size };

    if (kind === "feishu") {
        return <ChatBubbleIcon style={iconStyle} />;
    }
    if (kind === "oidc") {
        return <UserCircleIcon style={iconStyle} />;
    }

    return <LinkIcon style={iconStyle} />;
}

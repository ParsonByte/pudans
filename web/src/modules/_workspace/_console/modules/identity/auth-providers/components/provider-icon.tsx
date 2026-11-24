"use client";

import { NewLark } from "@icon-park/react";
import { KeyIcon, UserCircleIcon } from "tdesign-icons-react";
import { Avatar } from "tdesign-react";

import { useDesignTokens } from "@/components/ui";

import type { IdentityProviderKind } from "../composables/_auth-providers";

type ProviderIconProps = {
    kind: IdentityProviderKind;
    logo?: string;
    size?: number;
};

export function ProviderIcon({ kind, logo, size = 56 }: ProviderIconProps) {
    const tokens = useDesignTokens();

    if (logo) {
        return (
            <Avatar
                className="shrink-0"
                image={logo}
                shape="round"
                size={`${size}px`}
                style={{ border: `1px solid ${tokens.colorBorderSecondary}` }}
            />
        );
    }

    return (
        <span
            className="flex shrink-0 items-center justify-center rounded-2xl"
            style={{
                background: tokens.colorPrimaryBg,
                color: tokens.colorPrimary,
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
        return <NewLark size={size} />;
    }
    if (kind === "oidc") {
        return <UserCircleIcon style={iconStyle} />;
    }

    return <KeyIcon style={iconStyle} />;
}

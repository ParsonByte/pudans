"use client";

import { Avatar } from "tdesign-react";

import { useDesignTokens } from "@/components/ui";

type UserAvatarProps = {
    name?: string;
    size?: number;
    src?: string;
};

function initialsOf(name?: string): string {
    const text = name?.trim();

    if (!text) {
        return "?";
    }

    const parts = text.split(/\s+/).filter(Boolean);

    if (parts.length > 1) {
        return parts
            .slice(0, 2)
            .map((part) => Array.from(part)[0])
            .join("")
            .toUpperCase();
    }

    return Array.from(text).slice(0, 2).join("").toUpperCase();
}

export function UserAvatar({ name, size = 40, src }: UserAvatarProps) {
    const tokens = useDesignTokens();

    return (
        <Avatar
            image={src || undefined}
            shape="circle"
            size={`${size}px`}
            style={{
                backgroundColor: tokens.colorPrimaryBg,
                color: tokens.colorPrimary,
                flexShrink: 0,
            }}
        >
            {initialsOf(name)}
        </Avatar>
    );
}

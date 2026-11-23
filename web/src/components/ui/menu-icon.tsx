"use client";

import type { CSSProperties } from "react";
import { createElement } from "react";

import Image from "next/image";

import {
    AppIcon,
    DataBaseIcon,
    FileIcon,
    FileSettingIcon,
    FingerprintIcon,
    FolderOpenIcon,
    HistoryIcon,
    KeyIcon,
    LayoutIcon,
    LinkIcon,
    LockOnIcon,
    SettingIcon,
    ShieldErrorIcon,
    TagIcon,
    TerminalIcon,
    UserCircleIcon,
    UserListIcon,
    UserSettingIcon,
} from "tdesign-icons-react";

type MenuIconProps = {
    className?: string;
    icon?: string;
    size?: number;
    style?: CSSProperties;
};

type TDesignIcon = typeof AppIcon;

const MENU_ICONS: Record<string, TDesignIcon> = {
    "file-cog": FileSettingIcon,
    fingerprint: FingerprintIcon,
    "folder-open": FolderOpenIcon,
    history: HistoryIcon,
    "key-round": KeyIcon,
    "layout-dashboard": LayoutIcon,
    "link-2": LinkIcon,
    "lock-keyhole": LockOnIcon,
    "panel-top": LayoutIcon,
    "scroll-text": FileIcon,
    settings: SettingIcon,
    "settings-2": SettingIcon,
    "shield-alert": ShieldErrorIcon,
    "shield-check": ShieldErrorIcon,
    tags: TagIcon,
    terminal: TerminalIcon,
    "user-cog": UserSettingIcon,
    "user-round": UserCircleIcon,
    users: UserListIcon,
    "users-round": UserListIcon,
    waypoints: LinkIcon,
    webhook: LinkIcon,
};

function resolveIcon(icon?: string): TDesignIcon {
    const name = icon?.toLowerCase().replace(/^[a-z0-9-]+:/, "");

    return (name ? MENU_ICONS[name] : undefined) ?? (name?.includes("database") ? DataBaseIcon : AppIcon);
}

export function MenuIcon({ className, icon, size = 16, style }: MenuIconProps) {
    if (icon?.startsWith("http") || icon?.startsWith("/") || icon?.includes(".")) {
        const src = icon.startsWith("http") || icon.startsWith("/") ? icon : `/${icon}`;

        return <Image alt="" className={className} height={size} src={src} style={style} unoptimized width={size} />;
    }

    const Icon = resolveIcon(icon);

    return createElement(Icon, { className, size, style });
}

"use client";

import type { ReactNode } from "react";
import { useEffect } from "react";

import { ThemeMenu, UserMenu } from "@/components/ui";
import { useMobile } from "@/hooks/ui/use-mobile";
import { useThemeSync } from "@/hooks/ui/use-theme-sync";

import { AppContainer } from "./app-container";
import { AppHeader } from "./app-header";
import type { AppShellGroup, AppShellItem } from "./app-shell-types";
import { AppSidebar } from "./app-sidebar";

type AppShellProps = {
    actions?: ReactNode;
    activeGroup?: string;
    activeItem?: string;
    children: ReactNode;
    collapsed?: boolean;
    groups: AppShellGroup[];
    items: AppShellItem[];
    loading?: boolean;
    onCollapse?: (collapsed: boolean) => void;
    onNavigate: (href: string) => void;
    pageTitle?: string;
};

export function AppShell({
    actions = (
        <div className="app-shell__actions">
            <ThemeMenu />
            <UserMenu />
        </div>
    ),
    activeGroup,
    activeItem,
    children,
    collapsed = false,
    groups,
    items,
    loading = false,
    onCollapse,
    onNavigate,
    pageTitle,
}: AppShellProps) {
    const { isDark } = useThemeSync();
    const isCompact = useMobile();
    const menuCollapsed = isCompact ? !collapsed : collapsed;
    const onToggleSidebar = onCollapse ? () => onCollapse(!collapsed) : undefined;

    useEffect(() => {
        document.title = pageTitle ? `${pageTitle} - Pudans` : "Pudans";
    }, [pageTitle]);

    return (
        <div className="app-shell">
            <AppHeader
                actions={actions}
                activeGroup={activeGroup}
                collapsed={menuCollapsed}
                groups={groups}
                isDark={isDark}
                onNavigate={onNavigate}
                onToggleSidebar={onToggleSidebar}
            />
            <div className="app-shell__body">
                <AppSidebar
                    activeItem={activeItem}
                    collapsed={menuCollapsed}
                    isDark={isDark}
                    items={items}
                    loading={loading}
                    onNavigate={onNavigate}
                />
                <div className="app-shell__content">
                    <AppContainer>{children}</AppContainer>
                </div>
            </div>
        </div>
    );
}

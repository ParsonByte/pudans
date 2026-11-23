"use client";

import type { MouseEvent as ReactMouseEvent, ReactNode } from "react";
import { useEffect } from "react";

import Image from "next/image";
import Link from "next/link";

import { ViewListIcon } from "tdesign-icons-react";
import { Button, Loading, Menu } from "tdesign-react";

import { MenuIcon, ThemeMenu } from "@/components/ui";
import { useMobile } from "@/hooks/ui/use-mobile";
import { useThemeSync } from "@/hooks/ui/use-theme-sync";

import { AppContainer } from "./app-container";

const { HeadMenu, MenuGroup, MenuItem } = Menu;
const SIDE_NAVIGATION_ID = "workspace-side-navigation";

export type AppShellGroup = {
    href?: string;
    key: string;
    label: string;
};

type AppShellItemGroup = {
    key: string;
    label: string;
};

export type AppShellItem = {
    group?: AppShellItemGroup;
    href?: string;
    icon?: string;
    key: string;
    label: string;
};

type AppShellMenuSection = {
    items: AppShellItem[];
    key: string;
    label?: string;
};

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

function groupMenuItems(items: AppShellItem[]): AppShellMenuSection[] {
    const sections = new Map<string, AppShellMenuSection>();

    for (const item of items) {
        const key = item.group?.key ?? "items";
        const section = sections.get(key) ?? { items: [], key, label: item.group?.label };

        section.items.push(item);
        sections.set(key, section);
    }

    return [...sections.values()];
}

function navigateWithMenuLink(event: ReactMouseEvent<HTMLElement>, href: string, onNavigate: (href: string) => void) {
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
        return;
    }

    event.preventDefault();
    onNavigate(href);
}

function menuItemNode(
    item: AppShellItem,
    activeItem: string | undefined,
    onNavigate: (href: string) => void,
): ReactNode {
    const href = item.href;

    return (
        <MenuItem
            href={href}
            icon={item.icon ? <MenuIcon icon={item.icon} /> : undefined}
            key={item.key}
            onClick={
                href
                    ? ({ e }) => {
                          navigateWithMenuLink(e, href, onNavigate);
                      }
                    : undefined
            }
            value={item.key}
        >
            <span aria-current={activeItem === item.key ? "page" : undefined}>{item.label}</span>
        </MenuItem>
    );
}

function BrandLogo() {
    return (
        <Link aria-label="Pudans 首页" className="app-shell__brand" href="/">
            <Image alt="" height={32} src="/brand/icon-small.svg" unoptimized width={32} />
            <span className="app-shell__brand-title">Pudans</span>
        </Link>
    );
}

export function AppShell({
    actions = <ThemeMenu />,
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
    const menuCollapsed = isCompact || collapsed;
    const menuSections = groupMenuItems(items);

    useEffect(() => {
        document.title = pageTitle ? `${pageTitle} - Pudans` : "Pudans";
    }, [pageTitle]);

    return (
        <div className="app-shell">
            <header className="app-shell__header">
                <nav aria-label="工作区切换">
                    <HeadMenu
                        logo={<BrandLogo />}
                        operations={actions}
                        theme={isDark ? "dark" : "light"}
                        value={activeGroup}
                    >
                        {groups.map((group) => {
                            const href = group.href;

                            return (
                                <MenuItem
                                    href={href}
                                    key={group.key}
                                    onClick={
                                        href
                                            ? ({ e }) => {
                                                  navigateWithMenuLink(e, href, onNavigate);
                                              }
                                            : undefined
                                    }
                                    value={group.key}
                                >
                                    <span aria-current={activeGroup === group.key ? "page" : undefined}>
                                        {group.label}
                                    </span>
                                </MenuItem>
                            );
                        })}
                    </HeadMenu>
                </nav>
            </header>
            <div className="app-shell__body">
                <aside
                    className="app-shell__aside"
                    data-collapsed={menuCollapsed ? "true" : "false"}
                    id={SIDE_NAVIGATION_ID}
                >
                    <nav aria-label="工作区导航" className="app-shell__nav">
                        {loading ? (
                            <div className="app-shell__nav-loading" role="status">
                                <Loading size="small" />
                            </div>
                        ) : (
                            <Menu
                                collapsed={menuCollapsed}
                                operations={
                                    isCompact ? undefined : (
                                        <ButtonCollapse
                                            collapsed={collapsed}
                                            controls={SIDE_NAVIGATION_ID}
                                            onToggle={() => onCollapse?.(!collapsed)}
                                        />
                                    )
                                }
                                theme={isDark ? "dark" : "light"}
                                value={activeItem}
                                width={["240px", "64px"]}
                            >
                                {menuSections.map((section) =>
                                    section.label ? (
                                        <MenuGroup key={section.key} title={section.label}>
                                            {section.items.map((item) => menuItemNode(item, activeItem, onNavigate))}
                                        </MenuGroup>
                                    ) : (
                                        section.items.map((item) => menuItemNode(item, activeItem, onNavigate))
                                    ),
                                )}
                            </Menu>
                        )}
                    </nav>
                </aside>
                <div className="app-shell__content">
                    <AppContainer>{children}</AppContainer>
                </div>
            </div>
        </div>
    );
}

function ButtonCollapse({
    collapsed,
    controls,
    onToggle,
}: {
    collapsed: boolean;
    controls: string;
    onToggle: () => void;
}) {
    return (
        <Button
            aria-label={collapsed ? "展开侧边导航" : "收起侧边导航"}
            aria-controls={controls}
            aria-expanded={!collapsed}
            icon={<ViewListIcon />}
            onClick={onToggle}
            shape="square"
            variant="text"
        />
    );
}

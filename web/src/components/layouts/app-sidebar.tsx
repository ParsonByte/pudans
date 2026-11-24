"use client";

import type { ReactNode } from "react";

import { Loading, Menu } from "tdesign-react";

import { MenuIcon } from "@/components/ui";

import { navigateWithMenuLink } from "./app-navigation";
import type { AppShellItem } from "./app-shell-types";
import { SIDE_NAVIGATION_ID } from "./app-shell-types";

const { MenuGroup, MenuItem } = Menu;
const SIDEBAR_WIDTH = "232px";
const SIDEBAR_COLLAPSED_WIDTH = "48px";

type AppShellMenuSection = {
    items: AppShellItem[];
    key: string;
    label?: string;
};

type AppSidebarProps = {
    activeItem?: string;
    collapsed: boolean;
    isDark: boolean;
    items: AppShellItem[];
    loading: boolean;
    onNavigate: (href: string) => void;
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

export function AppSidebar({ activeItem, collapsed, isDark, items, loading, onNavigate }: AppSidebarProps) {
    const menuSections = groupMenuItems(items);

    return (
        <aside className="app-shell__aside" data-collapsed={collapsed ? "true" : "false"} id={SIDE_NAVIGATION_ID}>
            <nav aria-label="工作区导航" className="app-shell__nav">
                {loading ? (
                    <div className="app-shell__nav-loading" role="status">
                        <Loading size="small" />
                    </div>
                ) : (
                    <Menu
                        collapsed={collapsed}
                        theme={isDark ? "dark" : "light"}
                        value={activeItem}
                        width={[SIDEBAR_WIDTH, SIDEBAR_COLLAPSED_WIDTH]}
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
    );
}

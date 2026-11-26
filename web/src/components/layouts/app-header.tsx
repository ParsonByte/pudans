"use client";

import type { ReactNode } from "react";

import Image from "next/image";
import Link from "next/link";

import { ViewListIcon } from "tdesign-icons-react";
import { Button, Menu } from "tdesign-react";

import { navigateWithMenuLink } from "./app-navigation";
import type { AppShellGroup } from "./app-shell-types";
import { SIDE_NAVIGATION_ID } from "./app-shell-types";
import { AppSwitcher } from "./app-switcher";

const { HeadMenu, MenuItem, SubMenu } = Menu;

type AppHeaderProps = {
    actions?: ReactNode;
    activeGroup?: string;
    collapsed: boolean;
    groups: AppShellGroup[];
    isDark: boolean;
    onNavigate: (href: string) => void;
    onToggleSidebar?: () => void;
};

function BrandLogo() {
    return (
        <Link aria-label="Pudans 首页" className="app-shell__brand" href="/">
            <Image alt="" height={32} src="/brand/icon-small.svg" unoptimized width={32} />
            <span className="app-shell__brand-title">Pudans</span>
        </Link>
    );
}

function SidebarToggle({ collapsed, onToggle }: { collapsed: boolean; onToggle: () => void }) {
    return (
        <Button
            aria-label={collapsed ? "展开侧边导航" : "收起侧边导航"}
            aria-controls={SIDE_NAVIGATION_ID}
            aria-expanded={!collapsed}
            className="app-shell__sidebar-toggle"
            ghost
            icon={<ViewListIcon />}
            onClick={onToggle}
            shape="square"
            variant="text"
        />
    );
}

export function AppHeader({
    actions,
    activeGroup,
    collapsed,
    groups,
    isDark,
    onNavigate,
    onToggleSidebar,
}: AppHeaderProps) {
    return (
        <header className="app-shell__header">
            <nav aria-label="工作区切换">
                <HeadMenu
                    expandType="popup"
                    logo={
                        <div className="app-shell__brand-group">
                            <AppSwitcher />
                            <BrandLogo />
                            {onToggleSidebar ? (
                                <SidebarToggle collapsed={collapsed} onToggle={onToggleSidebar} />
                            ) : null}
                        </div>
                    }
                    operations={actions}
                    theme={isDark ? "dark" : "light"}
                    value={activeGroup}
                >
                    {groups.map((group) => {
                        if (group.children?.length) {
                            return (
                                <SubMenu
                                    key={group.key}
                                    popupProps={{ overlayStyle: { fontWeight: "normal" } }}
                                    title={group.label}
                                    value={group.key}
                                >
                                    {group.children.map((child) => (
                                        <MenuItem
                                            href={child.href}
                                            key={child.key}
                                            onClick={({ e }) => {
                                                navigateWithMenuLink(e, child.href, onNavigate);
                                            }}
                                            value={child.key}
                                        >
                                            {child.label}
                                        </MenuItem>
                                    ))}
                                </SubMenu>
                            );
                        }

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
                                <span aria-current={activeGroup === group.key ? "page" : undefined}>{group.label}</span>
                            </MenuItem>
                        );
                    })}
                </HeadMenu>
            </nav>
        </header>
    );
}

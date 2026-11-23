"use client";

import type { CSSProperties, ReactNode } from "react";

import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";

import { ProLayout, type ProLayoutProps } from "@ant-design/pro-components";
import { theme as antdTheme } from "antd";

import { useMediaQuery } from "@/hooks/ui/use-media-query";
import { useMobile } from "@/hooks/ui/use-mobile";
import { useThemeSync } from "@/hooks/ui/use-theme-sync";

import { AppContainer } from "./app-container";
import { AppHeader } from "./app-header";
import { AppSwitcher } from "./app-switcher";

type AppShellProps = {
    actions?: ReactNode;
    children: ReactNode;
    collapsed?: boolean;
    layout?: "mix" | "side" | "top";
    loading?: boolean;
    logo?: ReactNode;
    onCollapse?: (collapsed: boolean) => void;
    onOpenChange?: (openKeys: string[]) => void;
    openKeys?: string[];
    pageTitle?: string;
    route?: ProLayoutProps["route"];
    selectedKeys?: string[];
    title?: string;
};

export function AppShell({
    actions = <AppHeader />,
    children,
    collapsed,
    layout = "mix",
    loading = false,
    logo = <Image alt="" height={32} src="/brand/icon-small.svg" unoptimized width={32} />,
    onCollapse,
    onOpenChange,
    openKeys,
    pageTitle,
    route,
    selectedKeys,
    title = "Pudans",
}: AppShellProps) {
    const pathname = usePathname();
    const isDrawerMobile = useMediaQuery("(max-width: 575px)");
    const isMobile = useMobile();
    const { isDark } = useThemeSync();
    const { token } = antdTheme.useToken();

    return (
        <ProLayout
            actionsRender={() => (
                <div
                    className="flex items-center gap-1 ps-0 sm:gap-2 sm:ps-4"
                    style={isMobile ? undefined : { borderInlineStart: `1px solid ${token.colorSplit}` }}
                >
                    {actions}
                </div>
            )}
            collapsed={collapsed}
            fixedHeader
            fixSiderbar
            footerRender={false}
            headerContentRender={(_, defaultDom) => (
                <div
                    className="flex h-full min-w-0 flex-1 items-center ps-2 sm:ps-4 md:ps-6"
                    style={
                        {
                            "--ant-menu-horizontal-line-height": "36px",
                            "--ant-menu-item-height": "36px",
                            "--ant-menu-item-padding-inline": "12px",
                            lineHeight: "36px",
                        } as CSSProperties
                    }
                >
                    {defaultDom}
                </div>
            )}
            headerTitleRender={(logo, title) => (
                <div className="flex min-w-0 items-center gap-1.5 pe-2 sm:gap-2 sm:pe-4">
                    <span className="hidden md:inline-flex">
                        <AppSwitcher />
                    </span>
                    <span
                        aria-hidden="true"
                        className="hidden h-6 w-px sm:block"
                        style={{ background: token.colorSplit }}
                    />
                    {logo}
                    {title}
                </div>
            )}
            layout={layout}
            location={{ pathname }}
            logo={logo}
            menu={{ locale: false, loading }}
            menuProps={{
                style: {
                    backgroundColor: "transparent",
                    border: "none",
                    gap: 6,
                    width: "100%",
                } as CSSProperties,
            }}
            menuItemRender={(item, dom) => {
                if (!item.path || item.isUrl) {
                    return dom;
                }

                return (
                    <Link
                        href={item.path}
                        onClick={() => {
                            if (isDrawerMobile) {
                                onCollapse?.(true);
                            }
                        }}
                    >
                        {dom}
                    </Link>
                );
            }}
            navTheme={isDark ? "realDark" : "light"}
            onCollapse={onCollapse}
            onOpenChange={(keys) => {
                if (Array.isArray(keys)) {
                    onOpenChange?.(keys);
                }
            }}
            openKeys={openKeys}
            pageTitleRender={(props, defaultPageTitle) =>
                pageTitle ? `${pageTitle} - ${props.title ?? "Pudans"}` : (defaultPageTitle ?? "Pudans")
            }
            route={route}
            selectedKeys={selectedKeys}
            splitMenus
            title={title}
            token={{
                header: {
                    colorBgHeader: token.colorBgContainer,
                    colorBgMenuItemHover: token.colorFillTertiary,
                    colorBgMenuItemSelected: token.colorPrimary,
                    colorBgMenuElevated: token.colorBgElevated,
                    colorBgRightActionsItemHover: token.colorFillTertiary,
                    colorBgScrollHeader: token.colorBgContainer,
                    colorHeaderTitle: token.colorText,
                    colorTextMenu: token.colorTextSecondary,
                    colorTextMenuActive: token.colorText,
                    colorTextMenuSecondary: token.colorTextTertiary,
                    colorTextMenuSelected: token.colorTextLightSolid,
                    colorTextRightActionsItem: token.colorTextSecondary,
                    heightLayoutHeader: isMobile ? 56 : 64,
                },
                sider: {
                    colorBgCollapsedButton: token.colorBgElevated,
                    colorBgMenuItemHover: token.colorFillTertiary,
                    colorBgMenuItemSelected: token.colorPrimary,
                    colorMenuItemDivider: token.colorSplit,
                    colorMenuBackground: token.colorBgContainer,
                    colorTextCollapsedButton: token.colorTextTertiary,
                    colorTextCollapsedButtonHover: token.colorText,
                    colorTextMenu: token.colorTextSecondary,
                    colorTextMenuActive: token.colorText,
                    colorTextMenuItemHover: token.colorText,
                    colorTextMenuSelected: token.colorTextLightSolid,
                    menuHeight: 40,
                    paddingBlockLayoutMenu: 12,
                    paddingInlineLayoutMenu: 8,
                },
            }}
        >
            <AppContainer title={pageTitle}>{children}</AppContainer>
        </ProLayout>
    );
}

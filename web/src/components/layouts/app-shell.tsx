"use client";

import type { ReactNode } from "react";

import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";

import { ProLayout, type ProLayoutProps } from "@ant-design/pro-components";

import { NavigationProgress } from "@/components/ui";
import { useThemeSync } from "@/hooks/ui/use-theme-sync";

import { AppContainer } from "./app-container";
import { AppHeader } from "./app-header";

type AppShellProps = {
    actions?: ReactNode;
    children: ReactNode;
    collapsed?: boolean;
    layout?: "mix" | "side" | "top";
    loading?: boolean;
    logo?: ReactNode;
    onCollapse?: (collapsed: boolean) => void;
    openKeys?: string[];
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
    openKeys,
    route,
    selectedKeys,
    title = "Pudans",
}: AppShellProps) {
    const pathname = usePathname();
    const { isDark } = useThemeSync();

    return (
        <>
            <NavigationProgress />
            <ProLayout
                actionsRender={() => actions}
                collapsed={collapsed}
                fixedHeader
                fixSiderbar
                footerRender={false}
                layout={layout}
                location={{ pathname }}
                logo={logo}
                menu={{ locale: false, loading }}
                menuItemRender={(item, dom) => {
                    if (!item.path || item.isUrl) {
                        return dom;
                    }

                    return <Link href={item.path}>{dom}</Link>;
                }}
                navTheme={isDark ? "realDark" : "light"}
                onCollapse={onCollapse}
                openKeys={openKeys}
                route={route}
                selectedKeys={selectedKeys}
                splitMenus
                title={title}
            >
                <AppContainer>{children}</AppContainer>
            </ProLayout>
        </>
    );
}

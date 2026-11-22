"use client";

import { type ReactNode, Suspense, useEffect, useMemo, useState } from "react";

import Image from "next/image";
import { useRouter } from "next/navigation";

import { type MenuDataItem, type ProLayoutProps } from "@ant-design/pro-components";
import { Icon } from "@iconify/react";
import { Skeleton } from "antd";

import { AppShell } from "@/components/layouts";
import { type DynamicMenuGroup, type DynamicMenuSelection, useDynamicMenu } from "@/composables/useDynamicMenu";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { setWorkspaceCollapsed, useWorkspaceStore } from "@/stores/_workspace";

type WorkspaceRoute = NonNullable<ProLayoutProps["route"]>;

type WorkspaceLayoutProps = {
    children: ReactNode;
    workspace: string;
};

type WorkspaceRouteState = {
    openKeys: string[];
    route: WorkspaceRoute;
    selectedKeys: string[];
};

function renderIcon(icon?: string): ReactNode {
    if (!icon) {
        return undefined;
    }

    if (icon.startsWith("http") || icon.startsWith("/") || icon.includes(".")) {
        const src = icon.startsWith("http") || icon.startsWith("/") ? icon : `/${icon}`;

        return <Image alt="" height={16} src={src} unoptimized width={16} />;
    }

    return <Icon height={16} icon={icon} width={16} />;
}

function groupItemKey(group: DynamicMenuGroup): string {
    return `group:${group.key}`;
}

function parentItemKey(group: DynamicMenuGroup, parent: string): string {
    return `${group.key}:parent:${parent}`;
}

function menuItemKey(group: DynamicMenuGroup, itemKey: string): string {
    return `${group.key}:menu:${itemKey}`;
}

function buildMenuItems(group: DynamicMenuGroup): MenuDataItem[] {
    const items: MenuDataItem[] = [];
    const parents = new Map<string, MenuDataItem>();

    for (const menu of group.items) {
        const item: MenuDataItem = {
            key: menuItemKey(group, menu.itemKey),
            name: menu.label,
            path: menu.path,
            icon: renderIcon(menu.icon),
        };

        if (!menu.parent) {
            items.push(item);
            continue;
        }

        let parent = parents.get(menu.parent);

        if (!parent) {
            parent = {
                key: parentItemKey(group, menu.parent),
                name: menu.parent,
                icon: renderIcon(menu.parentIcon),
                children: [],
            };
            parents.set(menu.parent, parent);
            items.push(parent);
        }

        parent.children?.push(item);
    }

    return items;
}

function buildSelection(selection?: DynamicMenuSelection): Pick<WorkspaceRouteState, "openKeys" | "selectedKeys"> {
    if (!selection) {
        return { openKeys: [], selectedKeys: [] };
    }

    const parentKey = selection.item.parent ? parentItemKey(selection.group, selection.item.parent) : undefined;
    const selectedKeys = [
        groupItemKey(selection.group),
        ...(parentKey ? [parentKey] : []),
        menuItemKey(selection.group, selection.item.itemKey),
    ];
    const openKeys = parentKey ? [parentKey] : [];

    return { openKeys, selectedKeys };
}

function buildRoute(groups: DynamicMenuGroup[], selection?: DynamicMenuSelection): WorkspaceRouteState {
    const routes = groups.map((group) => ({
        key: groupItemKey(group),
        name: group.label,
        path: group.items[0]?.path,
        icon: renderIcon(group.icon),
        children: buildMenuItems(group),
    }));

    return {
        route: { path: "/", routes },
        ...buildSelection(selection),
    };
}

function WorkspaceLayoutContent({ children, workspace }: WorkspaceLayoutProps) {
    const router = useRouter();
    const dynamicMenu = useDynamicMenu(workspace);
    const prompt = useMessagePrompt();
    const { collapsed } = useWorkspaceStore();
    const [menuOpenState, setMenuOpenState] = useState<{ keys: string[]; routeKey: string }>();
    const routeState = useMemo(
        () => buildRoute(dynamicMenu.groups, dynamicMenu.selection),
        [dynamicMenu.groups, dynamicMenu.selection],
    );
    const menuRouteKey = routeState.selectedKeys.join("|");
    const openKeys = menuOpenState?.routeKey === menuRouteKey ? menuOpenState.keys : routeState.openKeys;

    useEffect(() => {
        if (dynamicMenu.loading || dynamicMenu.error || dynamicMenu.selectionFromQuery || !dynamicMenu.selectionPath) {
            return;
        }

        router.replace(dynamicMenu.selectionPath);
    }, [dynamicMenu.error, dynamicMenu.loading, dynamicMenu.selectionFromQuery, dynamicMenu.selectionPath, router]);

    useEffect(() => {
        if (!dynamicMenu.error) {
            return;
        }

        prompt.error({
            content: "工作区导航加载失败，点击重试。",
            key: "workspace-navigation-error",
            onClick: dynamicMenu.retry,
        });
    }, [dynamicMenu.error, dynamicMenu.retry, prompt]);

    return (
        <AppShell
            collapsed={collapsed}
            loading={dynamicMenu.loading}
            onCollapse={setWorkspaceCollapsed}
            onOpenChange={(keys) => setMenuOpenState({ keys, routeKey: menuRouteKey })}
            openKeys={openKeys}
            pageTitle={dynamicMenu.selection?.item.label ?? dynamicMenu.navigationWorkspace?.name}
            route={routeState.route}
            selectedKeys={routeState.selectedKeys}
        >
            {children}
        </AppShell>
    );
}

export function WorkspaceLayout({ children, workspace }: WorkspaceLayoutProps) {
    return (
        <Suspense
            fallback={
                <div className="p-6">
                    <Skeleton active />
                </div>
            }
        >
            <WorkspaceLayoutContent workspace={workspace}>{children}</WorkspaceLayoutContent>
        </Suspense>
    );
}

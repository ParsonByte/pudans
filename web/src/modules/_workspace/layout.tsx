"use client";

import { type ReactNode, Suspense, useEffect, useMemo, useRef } from "react";

import { useRouter } from "next/navigation";

import { apiClient } from "@pudans-dev/api-client";
import { Button, Loading } from "tdesign-react";

import { AppShell, type AppShellGroup, type AppShellGroupChild, type AppShellItem } from "@/components/layouts";
import { type DynamicMenuGroup, type DynamicMenuSelection, useDynamicMenu } from "@/composables/useDynamicMenu";
import { HOME_WORKSPACE_KEY } from "@/composables/workspace-navigation";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { IndexQuotaIndicator } from "@/modules/_workspace/_index/components/index-quota-indicator";
import { IndexSidebar } from "@/modules/_workspace/_index/components/index-sidebar";
import { setWorkspaceCollapsed, useWorkspaceStore } from "@/stores/_workspace";

const CONSOLE_WORKSPACE_KEY = "console";
const DANS_MENU_GROUP_KEY = "dans";
const LIVE_MENU_GROUP_KEY = "live";
const MORE_MENU_GROUP_KEY = "more";

type WorkspaceLayoutProps = {
    children: ReactNode;
    workspace: string;
};

type WorkspaceNavigationState = {
    activeGroup?: string;
    activeItem?: string;
    groups: AppShellGroup[];
    items: AppShellItem[];
};

function parentItemKey(group: DynamicMenuGroup, parent: string): string {
    return `${group.key}:parent:${parent}`;
}

function menuItemKey(group: DynamicMenuGroup, itemKey: string): string {
    return `${group.key}:menu:${itemKey}`;
}

function buildMenuItems(group: DynamicMenuGroup): AppShellItem[] {
    return group.items.map((menu) => ({
        group: menu.parent
            ? {
                  key: parentItemKey(group, menu.parent),
                  label: menu.parent,
              }
            : undefined,
        href: menu.path,
        icon: menu.icon,
        key: menuItemKey(group, menu.itemKey),
        label: menu.label,
    }));
}

function buildHeaderGroups(workspace: string, groups: DynamicMenuGroup[]): AppShellGroup[] {
    const isConsole = workspace === CONSOLE_WORKSPACE_KEY;
    const topLevelGroups = isConsole
        ? groups.filter((group) => group.key !== DANS_MENU_GROUP_KEY && group.key !== LIVE_MENU_GROUP_KEY)
        : groups;
    const headerGroups: AppShellGroup[] = topLevelGroups.map((group) => ({
        href: group.items[0]?.path,
        key: group.key,
        label: group.label,
    }));

    if (!isConsole) {
        return headerGroups;
    }

    const dansGroup = groups.find((group) => group.key === DANS_MENU_GROUP_KEY);
    const dansHref = dansGroup?.items[0]?.path;
    const liveGroup = groups.find((group) => group.key === LIVE_MENU_GROUP_KEY);
    const liveHref = liveGroup?.items[0]?.path;
    const children: AppShellGroupChild[] = [];

    if (dansGroup && dansHref) {
        children.push({
            href: dansHref,
            key: dansGroup.key,
            label: dansGroup.label,
        });
    }

    if (liveGroup && liveHref) {
        children.push({
            href: liveHref,
            key: liveGroup.key,
            label: liveGroup.label,
        });
    }

    if (children.length === 0) {
        return headerGroups;
    }

    headerGroups.push({
        children,
        key: MORE_MENU_GROUP_KEY,
        label: "更多",
    });

    return headerGroups;
}

function buildNavigation(
    workspace: string,
    groups: DynamicMenuGroup[],
    selection?: DynamicMenuSelection,
): WorkspaceNavigationState {
    const activeGroup = selection?.group.key;
    const activeItem = selection ? menuItemKey(selection.group, selection.item.itemKey) : undefined;
    const headerGroups = buildHeaderGroups(workspace, groups);

    return {
        activeGroup:
            workspace === CONSOLE_WORKSPACE_KEY &&
            (activeGroup === DANS_MENU_GROUP_KEY || activeGroup === LIVE_MENU_GROUP_KEY)
                ? MORE_MENU_GROUP_KEY
                : activeGroup,
        activeItem,
        groups: headerGroups.length > 1 || headerGroups.some((group) => group.children?.length) ? headerGroups : [],
        items: selection?.group ? buildMenuItems(selection.group) : [],
    };
}

function WorkspaceLayoutContent({ children, workspace }: WorkspaceLayoutProps) {
    const router = useRouter();
    const dynamicMenu = useDynamicMenu(workspace);
    const prompt = useMessagePrompt();
    const { collapsed } = useWorkspaceStore();
    const reportedNavigation = useRef<string | undefined>(undefined);
    const sidebar = workspace === HOME_WORKSPACE_KEY ? IndexSidebar : undefined;
    const navigation = useMemo(
        () => buildNavigation(workspace, dynamicMenu.groups, dynamicMenu.selection),
        [dynamicMenu.groups, dynamicMenu.selection, workspace],
    );

    useEffect(() => {
        if (
            workspace === HOME_WORKSPACE_KEY ||
            dynamicMenu.loading ||
            dynamicMenu.error ||
            dynamicMenu.selectionFromUrl ||
            !dynamicMenu.selectionPath
        ) {
            return;
        }

        router.replace(dynamicMenu.selectionPath);
    }, [
        dynamicMenu.error,
        dynamicMenu.loading,
        dynamicMenu.selectionFromUrl,
        dynamicMenu.selectionPath,
        router,
        workspace,
    ]);

    useEffect(() => {
        if (!dynamicMenu.error) {
            return;
        }

        prompt.error({
            content: "工作区导航加载失败。",
            footer: (
                <Button onClick={dynamicMenu.retry} size="small" variant="outline">
                    重试
                </Button>
            ),
            title: "导航不可用",
        });
    }, [dynamicMenu.error, dynamicMenu.retry, prompt]);

    useEffect(() => {
        if (!dynamicMenu.notFound) {
            return;
        }

        prompt.error({
            content: "当前工作区不存在或无权访问。",
            title: "工作区不可用",
        });
    }, [dynamicMenu.notFound, prompt]);

    useEffect(() => {
        const path = dynamicMenu.selectionPath;

        if (!path || dynamicMenu.loading || reportedNavigation.current === path) {
            return;
        }

        reportedNavigation.current = path;
        void apiClient.userNavigation.recordUserNavigation({ navigationEvent: { path } }).catch(() => {
            prompt.error({ content: "页面访问记录失败。" });
        });
    }, [dynamicMenu.loading, dynamicMenu.selectionPath, prompt]);

    return (
        <AppShell
            activeGroup={navigation.activeGroup}
            activeItem={navigation.activeItem}
            collapsed={collapsed}
            groups={navigation.groups}
            headerUtilities={workspace === HOME_WORKSPACE_KEY ? <IndexQuotaIndicator /> : undefined}
            items={navigation.items}
            loading={dynamicMenu.loading}
            onCollapse={setWorkspaceCollapsed}
            onNavigate={(href) => router.push(href)}
            pageTitle={dynamicMenu.selection?.item.label ?? dynamicMenu.navigationWorkspace?.name}
            sidebar={sidebar}
        >
            {children}
        </AppShell>
    );
}

export function WorkspaceLayout({ children, workspace }: WorkspaceLayoutProps) {
    return (
        <Suspense
            fallback={
                <div className="flex min-h-dvh items-center justify-center">
                    <Loading />
                </div>
            }
        >
            <WorkspaceLayoutContent workspace={workspace}>{children}</WorkspaceLayoutContent>
        </Suspense>
    );
}

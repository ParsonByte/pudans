"use client";

import { useMemo } from "react";

import { useSearchParams } from "next/navigation";

import { type NavigationMenu, type NavigationWorkspace, apiClient } from "@pudans-dev/api-client";
import { useQuery } from "@tanstack/react-query";

import { useEventCallback } from "@/hooks/utils/use-event-callback";

type DynamicMenuItem = {
    icon?: string;
    itemKey: string;
    label: string;
    menuKey: string;
    order: number;
    parent?: string;
    parentIcon?: string;
    path: string;
};

export type DynamicMenuGroup = {
    icon?: string;
    items: DynamicMenuItem[];
    key: string;
    label: string;
    order: number;
};

export type DynamicMenuSelection = {
    group: DynamicMenuGroup;
    item: DynamicMenuItem;
};

type DynamicMenuState = {
    error: unknown;
    groups: DynamicMenuGroup[];
    loading: boolean;
    navigationWorkspace?: NavigationWorkspace;
    notFound: boolean;
    retry: () => void;
    selection?: DynamicMenuSelection;
    selectionFromQuery: boolean;
    selectionPath?: string;
    workspaceKey?: string;
};

function workspaceMenuKey(menu: NavigationMenu): string | undefined {
    return menu.component?.trim() || undefined;
}

function workspaceMenuPath(workspace: string, group: string, menu: string): string {
    return `/workspace/${encodeURIComponent(workspace)}?${encodeURIComponent(group)}=${encodeURIComponent(menu)}`;
}

function buildGroups(workspace: string, menus: NavigationMenu[]): DynamicMenuGroup[] {
    const groups = new Map<string, DynamicMenuGroup>();

    for (const menu of menus) {
        const groupKey = menu.groupKey?.trim() || menu.group?.trim();
        const menuKey = workspaceMenuKey(menu);

        if (!groupKey || !menuKey) {
            continue;
        }

        const group = groups.get(groupKey) ?? {
            icon: menu.icon,
            items: [],
            key: groupKey,
            label: menu.group?.trim() || groupKey,
            order: menu.order ?? 0,
        };
        group.icon ??= menu.icon;
        group.order = Math.min(group.order, menu.order ?? group.order);
        group.items.push({
            icon: menu.icon,
            itemKey: menu.key ?? menuKey,
            label: menu.label ?? menuKey,
            menuKey,
            order: menu.order ?? 0,
            parent: menu.parent,
            parentIcon: menu.parentIcon,
            path: workspaceMenuPath(workspace, groupKey, menuKey),
        });
        groups.set(groupKey, group);
    }

    return [...groups.values()]
        .map((group) => ({
            ...group,
            items: group.items.sort((left, right) => left.order - right.order),
        }))
        .sort((left, right) => left.order - right.order || left.label.localeCompare(right.label));
}

function selectionFromQuery(
    groups: DynamicMenuGroup[],
    searchParams: URLSearchParams,
): DynamicMenuSelection | undefined {
    for (const group of groups) {
        const menuKey = searchParams.get(group.key);
        const item = group.items.find((candidate) => candidate.menuKey === menuKey);

        if (item) {
            return { group, item };
        }
    }

    return undefined;
}

function defaultSelection(groups: DynamicMenuGroup[]): DynamicMenuSelection | undefined {
    const group = groups.find((candidate) => candidate.items.length > 0);
    const item = group?.items[0];

    return group && item ? { group, item } : undefined;
}

export function useDynamicMenu(workspace: string): DynamicMenuState {
    const searchParams = useSearchParams();
    const search = searchParams.toString();
    const navigationQuery = useQuery({
        queryKey: ["navigation"],
        queryFn: async () => (await apiClient.navigation.getNavigation()).data,
        staleTime: 5 * 60 * 1000,
    });
    const state = useMemo(() => {
        const navigationWorkspace = navigationQuery.data?.workspaces?.find(
            (item) => (item.slug ?? item.id) === workspace,
        );
        const groups = buildGroups(workspace, navigationWorkspace?.menus ?? []);
        const requested = selectionFromQuery(groups, new URLSearchParams(search));
        const selection = requested ?? defaultSelection(groups);
        const selectionPath = selection
            ? workspaceMenuPath(workspace, selection.group.key, selection.item.menuKey)
            : undefined;

        return {
            error: navigationQuery.error,
            groups,
            loading: navigationQuery.isPending,
            navigationWorkspace,
            notFound: navigationQuery.isSuccess && !navigationWorkspace,
            selection,
            selectionFromQuery: Boolean(requested),
            selectionPath,
            workspaceKey: navigationWorkspace?.slug ?? navigationWorkspace?.id,
        };
    }, [
        navigationQuery.data,
        navigationQuery.error,
        navigationQuery.isPending,
        navigationQuery.isSuccess,
        search,
        workspace,
    ]);
    const retry = useEventCallback(() => void navigationQuery.refetch());

    return {
        ...state,
        retry,
    };
}

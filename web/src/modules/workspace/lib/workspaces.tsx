import type { ElementType } from "react";

import { Icon } from "@iconify/react";

import type { NavCollapsible, NavGroup, NavItem, Workspace } from "@/components/layout/types";
import { assetUrl } from "@/lib/asset-url";

import type { Navigation, NavigationMenu } from "./permission-api";

function navigationIcon(icon: string | undefined): ElementType {
  function NavigationIcon({ className }: { className?: string }) {
    if (icon && isImageIcon(icon)) {
      return <img src={assetUrl(icon)} alt="" className={className} aria-hidden="true" />;
    }
    return <Icon icon={icon || "lucide:circle"} className={className} aria-hidden="true" />;
  }

  return NavigationIcon;
}

function isImageIcon(icon: string): boolean {
  return icon.includes("/") || icon.startsWith("data:");
}

export function buildWorkspaces(navigation: Navigation): Workspace[] {
  return (navigation.workspaces ?? []).map((workspace) => ({
    id: workspace.id,
    name: workspace.name,
    plan: workspace.plan,
    logo: navigationIcon(workspace.icon),
    logoImage: workspace.icon ? isImageIcon(workspace.icon) : false,
    homeUrl: workspace.homePath,
    paths: (workspace.menus ?? []).map((menu) => menu.path),
  }));
}

export function buildWorkspaceNavGroups(navigation: Navigation, workspaceId: string): NavGroup[] {
  const workspace = (navigation.workspaces ?? []).find((item) => item.id === workspaceId);
  if (!workspace) {
    return [];
  }
  const groups = new Map<string, NavItem[]>();
  const parents = new Map<string, NavCollapsible>();
  for (const menu of workspace.menus ?? []) {
    const group = menu.group || workspace.name;
    const items = groups.get(group) ?? [];
    groups.set(group, items);
    if (menu.parent) {
      const parentKey = `${group}\n${menu.parent}`;
      let parent = parents.get(parentKey);
      if (!parent) {
        parent = {
          title: menu.parent,
          icon: navigationIcon(menu.parentIcon),
          items: [],
        };
        parents.set(parentKey, parent);
        items.push(parent);
      }
      parent.items.push({
        title: menu.label,
        url: menu.path,
        icon: navigationIcon(menu.icon),
      });
      continue;
    }
    items.push({
      title: menu.label,
      url: menu.path,
      icon: navigationIcon(menu.icon),
    });
  }
  return [...groups.entries()].map(([title, items]) => ({ title, items }));
}

export function findNavigationMenu(
  navigation: Navigation,
  pathname: string,
): NavigationMenu | undefined {
  const currentPath = normalizePath(pathname);
  let matched: { menu: NavigationMenu; length: number } | undefined;

  for (const workspace of navigation.workspaces ?? []) {
    for (const menu of workspace.menus ?? []) {
      const menuPath = normalizePath(menu.path);
      if (matchesPath(menuPath, currentPath) && (!matched || menuPath.length > matched.length)) {
        matched = { menu, length: menuPath.length };
      }
    }
  }

  return matched?.menu;
}

export function isPathAccessible(navigation: Navigation, pathname: string): boolean {
  return findNavigationMenu(navigation, pathname) !== undefined;
}

export function resolveActiveWorkspace(
  workspaces: readonly Workspace[],
  pathname: string,
): Workspace | undefined {
  const currentPath = normalizePath(pathname);
  let matched: { workspace: Workspace; length: number } | undefined;

  for (const workspace of workspaces) {
    for (const path of workspace.paths) {
      const workspacePath = normalizePath(path);
      if (
        matchesPath(workspacePath, currentPath) &&
        (!matched || workspacePath.length > matched.length)
      ) {
        matched = { workspace, length: workspacePath.length };
      }
    }
  }

  return matched?.workspace ?? workspaces[0];
}

function matchesPath(sourcePath: string, currentPath: string): boolean {
  return sourcePath === "/"
    ? currentPath === "/"
    : currentPath === sourcePath || currentPath.startsWith(`${sourcePath}/`);
}

function normalizePath(path: string): string {
  return path.replace(/\/+$/, "") || "/";
}

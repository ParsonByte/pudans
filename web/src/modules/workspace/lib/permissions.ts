import { useMemo } from "react";

import { useLocation, useRouteContext } from "@tanstack/react-router";

import { findNavigationMenu } from "./workspaces";

const UI_PERMISSION_ALL = "*";

export function hasUiPermission(
  permissions: readonly string[] | undefined,
  required?: string,
): boolean {
  if (!required) {
    return false;
  }
  return Boolean(permissions?.includes(UI_PERMISSION_ALL) || permissions?.includes(required));
}

export function useAllowedOperations(): ReadonlySet<string> {
  const { navigation } = useRouteContext({ from: "/_authenticated" });
  const pathname = useLocation({ select: (location) => location.pathname });
  const menu = findNavigationMenu(navigation, pathname);
  const operations = menu?.operations;

  return useMemo(
    () =>
      new Set(
        (operations ?? [])
          .filter((operation) => operation.allowed)
          .map((operation) => operation.key),
      ),
    [operations],
  );
}

export function useMenuPath(component: string): string | undefined {
  const { navigation } = useRouteContext({ from: "/_authenticated" });

  return (navigation.workspaces ?? [])
    .flatMap((workspace) => workspace.menus ?? [])
    .find((menu) => menu.component === component)?.path;
}

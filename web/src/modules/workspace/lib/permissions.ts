import { useMemo } from "react";

import { usePathname } from "next/navigation";

import { useSessionContext } from "@/modules/auth/lib/session-context";

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
  const { navigation } = useSessionContext();
  const pathname = usePathname();
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
  const { navigation } = useSessionContext();

  return (navigation.workspaces ?? [])
    .flatMap((workspace) => workspace.menus ?? [])
    .find((menu) => menu.component === component)?.path;
}

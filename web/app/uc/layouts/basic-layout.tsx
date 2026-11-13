import type { RegisteredMenu } from "@pudans/shared";
import { AppShell, type ShellSwitchTarget } from "../../../src/layouts/app-shell";

export interface BasicLayoutProps {
  menu: readonly RegisteredMenu[];
  switchTarget?: ShellSwitchTarget;
}

/** uc layout: dashboard-style dashboard shell. */
export function BasicLayout({ menu, switchTarget }: BasicLayoutProps) {
  return (
    <AppShell
      namespace="uc"
      basePath="/uc"
      brandSubKey="brandSub"
      menu={menu}
      switchTarget={switchTarget}
    />
  );
}

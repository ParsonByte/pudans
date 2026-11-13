import type { RegisteredMenu } from "@pudans/shared";
import { AppShell, type ShellSwitchTarget } from "../../src/layouts/app-shell";

export interface BasicLayoutProps {
  menu: readonly RegisteredMenu[];
  groupLabels: Readonly<Record<string, string>>;
  switchTarget?: ShellSwitchTarget;
}

/** Console layout: the shared fixed-sidebar shell with console labels. */
export function BasicLayout({ menu, groupLabels, switchTarget }: BasicLayoutProps) {
  return (
    <AppShell
      namespace="console"
      basePath="/console"
      brandSubKey="brandSub"
      menu={menu}
      groupLabels={groupLabels}
      switchTarget={switchTarget}
    />
  );
}

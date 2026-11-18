import { Link } from "@tanstack/react-router";
import { ArrowRight } from "lucide-react";

import {
  ConsoleEmpty,
  ConsolePage,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";
import type { Navigation } from "@/modules/workspace/lib/permission-api";
import { buildWorkspaces } from "@/modules/workspace/lib/workspaces";

type WorkspaceIndexViewProps = {
  navigation: Navigation;
};

export function WorkspaceIndexView({ navigation }: WorkspaceIndexViewProps) {
  const workspaces = buildWorkspaces(navigation);
  const availableWorkspaces = workspaces.filter((workspace) => workspace.homeUrl !== "/");

  return (
    <ConsolePage title="默认工作区">
      <ConsoleSection>
        <ConsoleSectionHeader
          title="可用工作区"
          description="选择工作区后，侧边栏会加载对应的菜单。"
        />
        <div className="p-5">
          {availableWorkspaces.length === 0 ? (
            <ConsoleEmpty>当前账号还没有其他工作区权限。</ConsoleEmpty>
          ) : (
            <div className="grid gap-3 md:grid-cols-2">
              {availableWorkspaces.map((workspace) => (
                <Link
                  key={workspace.id}
                  to={workspace.homeUrl}
                  className="group flex items-start justify-between gap-4 rounded-xl border bg-background p-5 transition-colors hover:border-primary/40 hover:bg-muted/40"
                >
                  <div className="flex min-w-0 items-start gap-3">
                    <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
                      <workspace.logo className="size-5" />
                    </span>
                    <div className="min-w-0 space-y-1">
                      <p className="truncate text-sm font-semibold">{workspace.name}</p>
                      <p className="truncate text-xs text-muted-foreground">{workspace.plan}</p>
                    </div>
                  </div>
                  <ArrowRight className="mt-1 size-4 shrink-0 text-muted-foreground transition-transform group-hover:translate-x-0.5" />
                </Link>
              ))}
            </div>
          )}
        </div>
      </ConsoleSection>
    </ConsolePage>
  );
}

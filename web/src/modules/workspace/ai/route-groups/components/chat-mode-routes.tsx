import { Link2, Pencil } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { ConsoleEmpty, ConsoleSection } from "@/modules/workspace/console/components/console-page";
import type { ChatMode } from "@/modules/workspace/index/lib/chat-api";
import { chatModeIcon, chatModeLabel } from "@/modules/workspace/index/lib/chat-functions";

import type { AiRouteGroupItem } from "../../providers/lib/api";
import { routeModeLabel } from "../../routing/lib/model";

type ChatModeRoutesProps = {
  modes: ChatMode[];
  routeGroups: AiRouteGroupItem[];
  loading: boolean;
  error: boolean;
  canUpdate: boolean;
  onEdit: (group: AiRouteGroupItem) => void;
};

export function ChatModeRoutes({
  modes,
  routeGroups,
  loading,
  error,
  canUpdate,
  onEdit,
}: ChatModeRoutesProps) {
  return (
    <ConsoleSection>
      <div className="flex flex-wrap items-start justify-between gap-3 border-b px-5 py-4">
        <div className="space-y-1">
          <h2 className="text-sm font-semibold">聊天模式路由</h2>
          <p className="text-xs text-muted-foreground">
            每个对话模式绑定一个路由组；聊天时只会在这些组之间选择。
          </p>
        </div>
        <Badge variant="outline">{modes.length} 个模式</Badge>
      </div>

      {loading ? (
        <div className="grid gap-3 px-5 py-5 sm:grid-cols-2">
          <Skeleton className="h-24 rounded-2xl" />
          <Skeleton className="h-24 rounded-2xl" />
        </div>
      ) : error ? (
        <p className="px-5 py-5 text-xs text-muted-foreground">无法加载聊天模式。</p>
      ) : modes.length === 0 ? (
        <ConsoleEmpty>还没有启用的聊天模式。</ConsoleEmpty>
      ) : (
        <div className="grid gap-3 px-5 py-5 sm:grid-cols-2">
          {modes.map((mode) => {
            const group = mode.routeGroup
              ? routeGroups.find((item) => item.metadata.name === mode.routeGroup)
              : undefined;
            const Icon = chatModeIcon(mode.name);
            return (
              <div
                key={mode.name}
                className="flex items-start gap-3 rounded-2xl border bg-card/40 p-4"
              >
                <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-background ring-1 ring-border/60">
                  <Icon className="size-5 text-muted-foreground" />
                </span>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-1.5">
                    <p className="truncate text-sm font-semibold">{chatModeLabel(mode)}</p>
                    <Badge variant="outline">{mode.name}</Badge>
                  </div>
                  {group ? (
                    <>
                      <p className="mt-1 flex items-center gap-1 truncate text-xs text-muted-foreground">
                        <Link2 className="size-3" />
                        {group.spec.displayName || group.metadata.name}
                      </p>
                      <p className="mt-0.5 text-[11px] text-muted-foreground">
                        {routeModeLabel(group.spec.routeMode)} · {(group.spec.members ?? []).length}{" "}
                        个模型成员
                      </p>
                    </>
                  ) : (
                    <p className="mt-1 text-xs text-destructive">
                      未绑定路由组{mode.routeGroup ? `：${mode.routeGroup}` : ""}
                    </p>
                  )}
                </div>
                {group && canUpdate ? (
                  <Button type="button" variant="ghost" size="sm" onClick={() => onEdit(group)}>
                    <Pencil />
                    编辑
                  </Button>
                ) : null}
              </div>
            );
          })}
        </div>
      )}
    </ConsoleSection>
  );
}

import type { ActiveUser } from "@pudans-dev/api-client";

import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import {
  ConsoleEmpty,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";

type ActiveUserRankingProps = {
  users: ActiveUser[];
};

export function ActiveUserRanking({ users }: ActiveUserRankingProps) {
  return (
    <ConsoleSection>
      <ConsoleSectionHeader
        title="活跃用户排行"
        description="最近 7 天操作次数"
        action={<Badge variant="secondary">TOP 5</Badge>}
      />
      {users.length === 0 ? (
        <ConsoleEmpty>暂无活跃用户数据。</ConsoleEmpty>
      ) : (
        <ol className="divide-y">
          {users.map((user, index) => (
            <li
              key={`${user.name ?? "user"}-${index}`}
              className="flex items-center gap-3 px-5 py-3"
            >
              <span
                className={cn(
                  "flex size-7 shrink-0 items-center justify-center rounded-md text-xs font-semibold",
                  index < 3
                    ? "bg-primary text-primary-foreground"
                    : "bg-muted text-muted-foreground",
                )}
              >
                {index + 1}
              </span>
              <span className="min-w-0 flex-1 truncate text-sm font-medium">
                {user.name || "未知用户"}
              </span>
              <span className="shrink-0 text-xs text-muted-foreground tabular-nums">
                {(user.operations ?? 0).toLocaleString("zh-CN")} 次
              </span>
            </li>
          ))}
        </ol>
      )}
    </ConsoleSection>
  );
}

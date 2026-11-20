import { BotOff, Loader2, Pencil, Play, Square, Terminal, Trash2 } from "lucide-react";

import { LongText } from "@/components/data-table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

import type { AiRuntimeAgentItem } from "../lib/api";
import {
  type AgentRuntimeRow,
  type RuntimeAction,
  agentDisplayName,
  agentStatusLabel,
  formatRuntimeDateTime,
  formatRuntimeLastActive,
  rowRuntimeProvider,
  runtimeProviderLabel,
  visibilityLabel,
} from "../lib/model";
import { RuntimeStateBadge } from "./runtime-badges";

type AgentRuntimeTableProps = {
  rows: readonly AgentRuntimeRow[];
  canToggle: boolean;
  canUpdate: boolean;
  canDelete: boolean;
  busyAgent?: string;
  onOpen: (name: string) => void;
  onEdit: (agent: AiRuntimeAgentItem) => void;
  onDeleteAgent: (agent: AiRuntimeAgentItem) => void;
  onAction: (name: string, action: RuntimeAction) => void;
};

export function AgentRuntimeTable({
  rows,
  canToggle,
  canUpdate,
  canDelete,
  busyAgent,
  onOpen,
  onEdit,
  onDeleteAgent,
  onAction,
}: AgentRuntimeTableProps) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="px-4">Agent</TableHead>
          <TableHead className="px-4">团队</TableHead>
          <TableHead className="px-4">可见性</TableHead>
          <TableHead className="px-4">状态</TableHead>
          <TableHead className="px-4">运行状态</TableHead>
          <TableHead className="px-4">提供方</TableHead>
          <TableHead className="px-4">沙箱 ID</TableHead>
          <TableHead className="px-4">最后活动</TableHead>
          <TableHead className="px-4 text-right">操作</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((row) => {
          const agent = row.agent;
          const busy = busyAgent === row.name;
          const running = row.runtime?.state === "RUNNING";
          const provider = rowRuntimeProvider(row);
          return (
            <TableRow key={row.name}>
              <TableCell className="px-4">
                <Button
                  type="button"
                  variant="ghost"
                  className="h-auto max-w-64 flex-col items-start gap-0.5 px-0 text-start hover:bg-transparent"
                  onClick={() => onOpen(row.name)}
                >
                  <span className="max-w-full truncate text-sm font-medium">
                    {agentDisplayName(agent, row.name)}
                  </span>
                  <span className="max-w-full truncate font-mono text-[11px] font-normal text-muted-foreground">
                    {row.name}
                  </span>
                </Button>
                {agent ? null : <Badge variant="outline">仅运行时</Badge>}
              </TableCell>
              <TableCell className="px-4 text-sm">{agent?.spec.team ?? "—"}</TableCell>
              <TableCell className="px-4">
                {agent ? (
                  <Badge variant="outline">{visibilityLabel(agent.spec.visibility)}</Badge>
                ) : (
                  <span className="text-sm text-muted-foreground">—</span>
                )}
              </TableCell>
              <TableCell className="px-4">
                {agent ? (
                  <Badge variant={agent.spec.status === "INACTIVE" ? "outline" : "secondary"}>
                    {agentStatusLabel(agent.spec.status)}
                  </Badge>
                ) : (
                  <span className="text-sm text-muted-foreground">—</span>
                )}
              </TableCell>
              <TableCell className="px-4">
                <div className="flex items-center gap-2">
                  <RuntimeStateBadge state={row.runtime?.state} />
                  {busy ? <Loader2 className="size-4 animate-spin text-muted-foreground" /> : null}
                </div>
                {row.runtime?.lastError ? (
                  <p className="mt-1 max-w-52 truncate text-[11px] text-destructive">
                    {row.runtime.lastError}
                  </p>
                ) : null}
              </TableCell>
              <TableCell className="px-4 text-sm">{runtimeProviderLabel(provider)}</TableCell>
              <TableCell className="max-w-44 px-4">
                <LongText className="font-mono text-xs">{row.runtime?.sandboxId ?? "—"}</LongText>
              </TableCell>
              <TableCell className="px-4">
                <p className="text-sm">{formatRuntimeLastActive(row.runtime?.lastActiveAt)}</p>
                <p className="text-[11px] text-muted-foreground">
                  {formatRuntimeDateTime(row.runtime?.lastActiveAt)}
                </p>
              </TableCell>
              <TableCell className="px-4 text-right">
                <div className="flex items-center justify-end gap-1">
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon-sm"
                    aria-label="打开运行时控制台"
                    title="打开运行时控制台"
                    onClick={() => onOpen(row.name)}
                    disabled={busy}
                  >
                    <Terminal />
                  </Button>
                  {canToggle && !running ? (
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon-sm"
                      aria-label="启动运行时"
                      title={provider === "NONE" ? "请先为 Agent 配置沙箱提供方" : "启动运行时"}
                      onClick={() => onAction(row.name, "start")}
                      disabled={busy || provider === "NONE"}
                    >
                      <Play />
                    </Button>
                  ) : null}
                  {canToggle && running ? (
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon-sm"
                      aria-label="停止运行时"
                      title="停止运行时"
                      onClick={() => onAction(row.name, "stop")}
                      disabled={busy}
                    >
                      <Square />
                    </Button>
                  ) : null}
                  {canUpdate && agent ? (
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon-sm"
                      aria-label="编辑 Agent"
                      title="编辑 Agent"
                      onClick={() => onEdit(agent)}
                      disabled={busy}
                    >
                      <Pencil />
                    </Button>
                  ) : null}
                  {canDelete && agent ? (
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon-sm"
                      className="text-destructive hover:text-destructive"
                      aria-label="删除 Agent"
                      title="删除 Agent"
                      onClick={() => onDeleteAgent(agent)}
                      disabled={busy}
                    >
                      <BotOff />
                    </Button>
                  ) : null}
                  {canDelete && row.runtime ? (
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon-sm"
                      className="text-destructive hover:text-destructive"
                      aria-label="销毁运行时"
                      title="销毁运行时"
                      onClick={() => onAction(row.name, "destroy")}
                      disabled={busy}
                    >
                      <Trash2 />
                    </Button>
                  ) : null}
                </div>
              </TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}

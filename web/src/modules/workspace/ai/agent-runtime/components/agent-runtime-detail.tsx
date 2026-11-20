import type { ReactNode } from "react";

import {
  BotOff,
  CircleAlert,
  Loader2,
  Pencil,
  Play,
  RotateCcw,
  Square,
  Trash2,
} from "lucide-react";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";

import type { AiRuntimeAgentItem } from "../lib/api";
import {
  type AgentRuntimeRow,
  RELATED_RESOURCE_FIELDS,
  type RuntimeAction,
  agentDisplayName,
  agentStatusLabel,
  formatRuntimeDateTime,
  formatRuntimeLastActive,
  relatedResourceCount,
  rowRuntimeProvider,
  runtimeProviderLabel,
  visibilityLabel,
} from "../lib/model";
import { CommandConsole } from "./command-console";
import { FilePanel } from "./file-panel";
import { LogConsole } from "./log-console";
import { RuntimeProviderBadge, RuntimeStateBadge } from "./runtime-badges";

type AgentRuntimeDetailProps = {
  row: AgentRuntimeRow;
  canToggle: boolean;
  canDelete: boolean;
  canUpdate: boolean;
  canExec: boolean;
  canFiles: boolean;
  canLogs: boolean;
  busy: boolean;
  onAction: (name: string, action: RuntimeAction) => void;
  onEdit: (agent: AiRuntimeAgentItem) => void;
  onDeleteAgent: (agent: AiRuntimeAgentItem) => void;
  onClose: () => void;
};

export function AgentRuntimeDetail({
  row,
  canToggle,
  canDelete,
  canUpdate,
  canExec,
  canFiles,
  canLogs,
  busy,
  onAction,
  onEdit,
  onDeleteAgent,
  onClose,
}: AgentRuntimeDetailProps) {
  const agent = row.agent;
  const runtime = row.runtime;
  const spec = agent?.spec;
  const provider = rowRuntimeProvider(row);
  const running = runtime?.state === "RUNNING";
  const workdir = runtimeWorkdir(row);
  const timeoutLimit = runtime?.timeoutSeconds ?? spec?.runtime?.timeoutSeconds ?? null;
  const tabs = [
    { value: "exec", label: "命令", enabled: canExec },
    { value: "files", label: "文件", enabled: canFiles },
    { value: "logs", label: "日志", enabled: canLogs },
  ].filter((tab) => tab.enabled);

  return (
    <Sheet open onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="flex w-full flex-col gap-0 sm:max-w-3xl">
        <SheetHeader className="gap-2 border-b px-6 py-5">
          <div className="flex flex-wrap items-center gap-2 pe-8">
            <SheetTitle>{agentDisplayName(agent, row.name)}</SheetTitle>
            <RuntimeStateBadge state={runtime?.state} />
            <RuntimeProviderBadge provider={provider} />
            {busy ? <Loader2 className="size-4 animate-spin text-muted-foreground" /> : null}
          </div>
          <SheetDescription className="font-mono">{row.name}</SheetDescription>
          <div className="flex flex-wrap gap-2">
            {canToggle ? (
              <Button
                type="button"
                size="sm"
                onClick={() => onAction(row.name, "start")}
                disabled={busy || provider === "NONE" || running}
                title={provider === "NONE" ? "请先为 Agent 配置沙箱提供方" : undefined}
              >
                <Play />
                启动
              </Button>
            ) : null}
            {canToggle ? (
              <Button
                type="button"
                size="sm"
                variant="outline"
                onClick={() => onAction(row.name, "stop")}
                disabled={busy || !running}
              >
                <Square />
                停止
              </Button>
            ) : null}
            {canToggle ? (
              <Button
                type="button"
                size="sm"
                variant="outline"
                onClick={() => onAction(row.name, "restart")}
                disabled={busy || !runtime || provider === "NONE"}
              >
                <RotateCcw />
                重启
              </Button>
            ) : null}
            {canDelete && runtime ? (
              <Button
                type="button"
                size="sm"
                variant="destructive"
                onClick={() => onAction(row.name, "destroy")}
                disabled={busy}
              >
                <Trash2 />
                销毁
              </Button>
            ) : null}
            {canUpdate && agent ? (
              <Button
                type="button"
                size="sm"
                variant="ghost"
                onClick={() => onEdit(agent)}
                disabled={busy}
              >
                <Pencil />
                编辑配置
              </Button>
            ) : null}
            {canDelete && agent ? (
              <Button
                type="button"
                size="sm"
                variant="outline"
                className="text-destructive hover:text-destructive"
                onClick={() => onDeleteAgent(agent)}
                disabled={busy}
              >
                <BotOff />
                删除 Agent
              </Button>
            ) : null}
          </div>
        </SheetHeader>

        <div className="min-h-0 flex-1 space-y-5 overflow-y-auto px-6 py-5">
          {runtime?.lastError ? (
            <Alert variant="destructive">
              <CircleAlert />
              <AlertTitle>运行时错误</AlertTitle>
              <AlertDescription className="font-mono text-xs break-all">
                {runtime.lastError}
              </AlertDescription>
            </Alert>
          ) : null}

          <section className="space-y-3">
            <h3 className="text-sm font-semibold">运行时状态</h3>
            <div className="grid gap-3 sm:grid-cols-2">
              <InfoItem label="运行状态" value={<RuntimeStateBadge state={runtime?.state} />} />
              <InfoItem label="提供方" value={runtimeProviderLabel(provider)} />
              <InfoItem label="沙箱 ID" value={runtime?.sandboxId ?? "—"} mono />
              <InfoItem label="接入地址" value={runtime?.endpoint ?? "—"} mono />
              <InfoItem
                label="镜像"
                value={runtime?.image ?? spec?.runtime?.image ?? "使用提供方默认镜像"}
                mono
              />
              <InfoItem
                label="CPU / 内存"
                value={`${runtime?.cpu ?? spec?.runtime?.cpu ?? "—"} vCPU / ${
                  runtime?.memoryMb ?? spec?.runtime?.memoryMb ?? "—"
                } MB`}
              />
              <InfoItem label="工作目录" value={workdir} mono />
              <InfoItem
                label="超时上限"
                value={timeoutLimit ? `${timeoutLimit} 秒` : "运行时默认"}
              />
              <InfoItem
                label="最后活动"
                value={`${formatRuntimeLastActive(runtime?.lastActiveAt)} · ${formatRuntimeDateTime(
                  runtime?.lastActiveAt,
                )}`}
              />
              <InfoItem label="创建时间" value={formatRuntimeDateTime(runtime?.createdAt)} />
            </div>
          </section>

          {agent ? (
            <section className="space-y-3">
              <h3 className="text-sm font-semibold">Agent 配置</h3>
              <div className="grid gap-3 sm:grid-cols-2">
                <InfoItem label="团队" value={spec?.team ?? "—"} mono />
                <InfoItem label="所有者" value={spec?.ownerUserId ?? "—"} mono />
                <InfoItem
                  label="可见性"
                  value={<Badge variant="outline">{visibilityLabel(spec?.visibility)}</Badge>}
                />
                <InfoItem
                  label="Agent 状态"
                  value={
                    <Badge variant={spec?.status === "INACTIVE" ? "outline" : "secondary"}>
                      {agentStatusLabel(spec?.status)}
                    </Badge>
                  }
                />
                <InfoItem label="默认路由组" value={spec?.defaultRouteGroup ?? "—"} />
                <InfoItem label="默认对话模式" value={spec?.defaultChatMode ?? "—"} />
                <InfoItem label="自动启动" value={spec?.runtime?.autoStart ? "已开启" : "关闭"} />
                <InfoItem
                  label="网络访问"
                  value={spec?.runtime?.networkEnabled === false ? "关闭" : "开启"}
                />
              </div>
              <div className="flex flex-wrap gap-1.5">
                {RELATED_RESOURCE_FIELDS.map((field) => (
                  <Badge key={field.key} variant="outline">
                    {field.label} {relatedResourceCount(spec, field.key)}
                  </Badge>
                ))}
              </div>
              {spec?.prompt ? (
                <div className="space-y-1">
                  <p className="text-xs font-medium text-muted-foreground">系统提示词</p>
                  <pre className="max-h-40 overflow-auto rounded-lg border bg-muted/40 p-3 text-xs break-words whitespace-pre-wrap">
                    {spec.prompt}
                  </pre>
                </div>
              ) : null}
            </section>
          ) : (
            <p className="rounded-lg border border-dashed px-3 py-4 text-xs text-muted-foreground">
              该运行时对应的 Agent 不在当前可见的 Agent 列表中，只能查看和操作运行时。
            </p>
          )}

          <section className="space-y-3">
            <h3 className="text-sm font-semibold">运行时控制台</h3>
            {tabs.length === 0 ? (
              <p className="rounded-lg border border-dashed px-3 py-6 text-center text-xs text-muted-foreground">
                当前角色没有命令、文件或日志操作权限。
              </p>
            ) : (
              <Tabs defaultValue={tabs[0]?.value} className="space-y-3">
                <TabsList variant="line" className="w-full justify-start border-b pb-1">
                  {tabs.map((tab) => (
                    <TabsTrigger key={tab.value} value={tab.value} className="flex-none px-3">
                      {tab.label}
                    </TabsTrigger>
                  ))}
                </TabsList>
                {canExec ? (
                  <TabsContent value="exec">
                    <CommandConsole
                      agent={row.name}
                      running={Boolean(running)}
                      defaultWorkdir={workdir}
                      timeoutLimit={timeoutLimit}
                    />
                  </TabsContent>
                ) : null}
                {canFiles ? (
                  <TabsContent value="files">
                    <FilePanel agent={row.name} running={Boolean(running)} />
                  </TabsContent>
                ) : null}
                {canLogs ? (
                  <TabsContent value="logs">
                    <LogConsole
                      agent={row.name}
                      running={Boolean(running)}
                      available={Boolean(runtime)}
                    />
                  </TabsContent>
                ) : null}
              </Tabs>
            )}
          </section>
        </div>
      </SheetContent>
    </Sheet>
  );
}

function runtimeWorkdir(row: AgentRuntimeRow): string {
  return row.agent?.spec.runtime?.workdir?.trim() || "/workspace";
}

type InfoItemProps = {
  label: string;
  value: ReactNode;
  mono?: boolean;
};

function InfoItem({ label, value, mono = false }: InfoItemProps) {
  return (
    <div className="min-w-0 rounded-lg border bg-card/40 px-3 py-2">
      <p className="text-[11px] text-muted-foreground">{label}</p>
      <div className={`min-w-0 truncate text-sm ${mono ? "font-mono text-xs" : ""}`}>{value}</div>
    </div>
  );
}

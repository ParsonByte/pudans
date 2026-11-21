import { useMemo, useState } from "react";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Loader2, RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { MarkdownContent } from "@/components/content/markdown";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { agentHint, agentLabel, useAgentsQuery } from "@/modules/workspace/ai/library/lib/agents";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";

import {
  type MemoryPromptItem,
  type MemoryPromptLayer,
  type MemoryPromptTargetType,
  buildMemoryPromptItem,
  createMemoryPrompt,
  deleteMemoryPrompt,
  patchMemoryPrompt,
  resolveMemoryApiError,
  setMemoryPrompt,
  updateMemoryPrompt,
} from "../lib/api";
import {
  MEMORY_PROMPT_ACTION_LABELS,
  MEMORY_PROMPT_LAYER_OPTIONS,
  MEMORY_PROMPT_SOURCE_LABELS,
  MEMORY_PROMPT_TARGET_LABELS,
  MEMORY_PROMPT_TARGET_TYPES,
} from "../lib/model";
import {
  invalidateMemoryPromptQueries,
  promptSettingTarget,
  useMemoryPromptDetail,
  useMemoryPromptLogs,
  useMemoryPromptSetting,
  useMemoryPrompts,
} from "../lib/queries";
import { MemoryConfirmDialog } from "./memory-confirm-dialogs";
import { MemoryPromptBindingsCard } from "./memory-prompt-bindings";
import {
  MemoryPromptCreateDialog,
  type MemoryPromptCreateValues,
  MemoryPromptRenameDialog,
} from "./memory-prompt-dialogs";
import { MemoryEmptyState, MemoryErrorState } from "./memory-states";

type MemoryPromptsTabProps = {
  team: string;
  canManage: boolean;
  currentUserId?: string;
};

function PromptEditor({
  item,
  targetLabel,
  canManage,
  saving,
  applying,
  onSave,
  onApply,
}: {
  item: MemoryPromptItem;
  targetLabel: string;
  canManage: boolean;
  saving: boolean;
  applying: boolean;
  onSave: (content: string) => void;
  onApply: (content: string) => void;
}) {
  const [content, setContent] = useState(item.spec.prompt);
  const pending = saving || applying || !canManage;

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-mono text-xs">{item.metadata.name}</span>
        <Badge variant="outline">v{item.spec.version ?? 1}</Badge>
        {item.spec.status && item.spec.status !== "ACTIVE" ? (
          <Badge variant="outline">{item.spec.status}</Badge>
        ) : null}
        {item.spec.updatedBy ? (
          <span className="text-[11px] text-muted-foreground">更新人 {item.spec.updatedBy}</span>
        ) : null}
      </div>
      <Textarea
        value={content}
        onChange={(event) => setContent(event.target.value)}
        className="min-h-[360px] resize-y font-mono text-[13px] leading-relaxed"
        disabled={pending}
        aria-label="提示词内容"
      />
      <div className="flex flex-wrap items-center gap-2">
        <Button type="button" variant="outline" disabled={pending} onClick={() => onSave(content)}>
          {saving ? <Loader2 className="animate-spin" /> : null}
          保存提示词
        </Button>
        <Button type="button" disabled={pending} onClick={() => onApply(content)}>
          {applying ? <Loader2 className="animate-spin" /> : null}
          {targetLabel}
        </Button>
        <span className="text-xs text-muted-foreground">
          {!canManage
            ? "仅管理员可编辑提示词"
            : content === item.spec.prompt
              ? "无未保存修改"
              : "有未保存修改，应用时会先保存"}
        </span>
      </div>
    </div>
  );
}

export function MemoryPromptsTab({ team, canManage, currentUserId }: MemoryPromptsTabProps) {
  const queryClient = useQueryClient();
  const [layer, setLayer] = useState<MemoryPromptLayer>("L1");
  const [targetType, setTargetType] = useState<MemoryPromptTargetType>("TEAM");
  const [agentName, setAgentName] = useState("");
  const [selectedPromptName, setSelectedPromptName] = useState("");
  const [clearOpen, setClearOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [renameOpen, setRenameOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);

  const agentsQuery = useAgentsQuery();
  const teamAgents = useMemo(
    () => (agentsQuery.data ?? []).filter((agent) => !agent.spec.team || agent.spec.team === team),
    [agentsQuery.data, team],
  );

  const promptsQuery = useMemoryPrompts();
  const promptOptions = useMemo(
    () =>
      (promptsQuery.data ?? []).filter(
        (prompt) => prompt.spec.layer === layer && prompt.spec.status !== "DELETING",
      ),
    [promptsQuery.data, layer],
  );
  const selectedSummary =
    promptOptions.find((prompt) => prompt.metadata.name === selectedPromptName) ?? promptOptions[0];
  const detailQuery = useMemoryPromptDetail(
    selectedSummary?.metadata.name,
    Boolean(selectedSummary),
  );
  const selectedPrompt = detailQuery.data ?? selectedSummary;

  const target = promptSettingTarget(targetType, team, agentName);
  const canResolve = targetType !== "AGENT" || Boolean(agentName);
  const settingQuery = useMemoryPromptSetting({ layer, ...target }, canResolve);
  const logsQuery = useMemoryPromptLogs(
    { promptId: selectedPrompt?.metadata.name, limit: 20 },
    true,
  );

  const saveMutation = useMutation({
    mutationFn: async (input: { item: MemoryPromptItem; content: string }) =>
      updateMemoryPrompt({
        ...input.item,
        spec: { ...input.item.spec, prompt: input.content },
      }),
    onSuccess: async () => {
      await invalidateMemoryPromptQueries(queryClient);
      toast.success("提示词已保存");
    },
    onError: (error) => {
      toast.error(resolveMemoryApiError(error));
    },
  });

  const applyMutation = useMutation({
    mutationFn: async (input: { item: MemoryPromptItem; content: string }) => {
      const stored =
        input.content === input.item.spec.prompt
          ? input.item
          : await updateMemoryPrompt({
              ...input.item,
              spec: { ...input.item.spec, prompt: input.content },
            });
      await setMemoryPrompt({
        name: stored.metadata.name,
        layer: stored.spec.layer,
        targetType,
        team: targetType === "INSTANCE" ? undefined : team,
        agent: targetType === "AGENT" ? agentName : undefined,
        action: "APPLY",
      });
    },
    onSuccess: async () => {
      await invalidateMemoryPromptQueries(queryClient);
      toast.success("提示词已应用");
    },
    onError: (error) => {
      toast.error(resolveMemoryApiError(error));
    },
  });

  const createOpenMutation = useMutation({
    mutationFn: (values: MemoryPromptCreateValues) =>
      createMemoryPrompt(
        buildMemoryPromptItem({
          name: values.name,
          displayName: values.displayName,
          layer: values.layer,
          prompt: values.prompt,
        }),
      ),
    onSuccess: async (item) => {
      await invalidateMemoryPromptQueries(queryClient);
      setLayer(item.spec.layer);
      setSelectedPromptName(item.metadata.name);
      setCreateOpen(false);
      toast.success("提示词已创建");
    },
    onError: (error) => {
      toast.error(resolveMemoryApiError(error));
    },
  });

  const renameMutation = useMutation({
    mutationFn: (input: { name: string; displayName: string }) =>
      patchMemoryPrompt(input.name, [
        { op: "replace", path: "/spec/name", value: input.displayName },
      ]),
    onSuccess: async () => {
      await invalidateMemoryPromptQueries(queryClient);
      setRenameOpen(false);
      toast.success("提示词显示名称已更新");
    },
    onError: (error) => {
      toast.error(resolveMemoryApiError(error));
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (name: string) => deleteMemoryPrompt(name),
    onSuccess: async (_, name) => {
      await invalidateMemoryPromptQueries(queryClient);
      if (selectedPrompt?.metadata.name === name) {
        setSelectedPromptName("");
      }
      setDeleteOpen(false);
      toast.success("提示词已删除");
    },
    onError: (error) => {
      toast.error(resolveMemoryApiError(error));
    },
  });

  const clearName = settingQuery.data?.promptId ?? selectedPrompt?.metadata.name ?? "";
  const canClear = settingQuery.data?.source === targetType && Boolean(clearName);

  const clearMutation = useMutation({
    mutationFn: async () => {
      await setMemoryPrompt({
        name: clearName,
        layer,
        targetType,
        team: targetType === "INSTANCE" ? undefined : team,
        agent: targetType === "AGENT" ? agentName : undefined,
        action: "CLEAR",
      });
    },
    onSuccess: async () => {
      await invalidateMemoryPromptQueries(queryClient);
      toast.success("已清除目标提示词");
      setClearOpen(false);
    },
    onError: (error) => {
      toast.error(resolveMemoryApiError(error));
    },
  });

  const targetSuffix =
    targetType === "AGENT" ? agentName || "未选择 Agent" : targetType === "TEAM" ? team : "实例";
  const targetLabel = `应用到${MEMORY_PROMPT_TARGET_LABELS[targetType]} · ${targetSuffix}`;

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <ToggleGroup
          type="single"
          variant="outline"
          size="sm"
          value={layer}
          onValueChange={(value) => {
            if (!value) {
              return;
            }
            setLayer(value as MemoryPromptLayer);
            setSelectedPromptName("");
          }}
        >
          {MEMORY_PROMPT_LAYER_OPTIONS.map((option) => (
            <ToggleGroupItem key={option} value={option}>
              {option}
            </ToggleGroupItem>
          ))}
        </ToggleGroup>
        <Select
          value={targetType}
          onValueChange={(value) => setTargetType(value as MemoryPromptTargetType)}
        >
          <SelectTrigger className="w-32" size="sm" aria-label="提示词目标类型">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {MEMORY_PROMPT_TARGET_TYPES.map((type) => (
              <SelectItem key={type} value={type}>
                {MEMORY_PROMPT_TARGET_LABELS[type]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        {targetType === "AGENT" ? (
          <Select value={agentName} onValueChange={setAgentName}>
            <SelectTrigger className="w-52" size="sm" aria-label="提示词目标 Agent">
              <SelectValue placeholder="选择 Agent" />
            </SelectTrigger>
            <SelectContent>
              {teamAgents.map((agent) => {
                const hint = agentHint(agent);
                return (
                  <SelectItem key={agent.metadata.name} value={agent.metadata.name}>
                    {agentLabel(agent)}
                    {hint ? ` · ${hint}` : ""}
                  </SelectItem>
                );
              })}
            </SelectContent>
          </Select>
        ) : null}
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={!canResolve || settingQuery.isFetching}
          onClick={() => settingQuery.refetch()}
        >
          <RefreshCw className={settingQuery.isFetching ? "animate-spin" : ""} />
          刷新生效
        </Button>
        <Button
          type="button"
          size="sm"
          className="ms-auto"
          disabled={!canManage}
          onClick={() => setCreateOpen(true)}
        >
          新建提示词
        </Button>
      </div>

      <div className="border bg-card p-3">
        <div className="mb-2 flex flex-wrap items-center gap-2">
          <span className="text-sm font-medium">当前生效提示词</span>
          {settingQuery.data ? (
            <>
              <Badge variant="secondary">
                {MEMORY_PROMPT_SOURCE_LABELS[settingQuery.data.source] ?? settingQuery.data.source}
              </Badge>
              <Badge variant="outline">v{settingQuery.data.version}</Badge>
            </>
          ) : null}
          {canManage && canClear ? (
            <Button
              type="button"
              variant="destructive"
              size="xs"
              className="ms-auto"
              disabled={clearMutation.isPending}
              onClick={() => setClearOpen(true)}
            >
              清除当前目标
            </Button>
          ) : null}
        </div>
        {!canResolve ? (
          <p className="text-xs text-muted-foreground">选择 Agent 后查看生效提示词。</p>
        ) : settingQuery.isLoading ? (
          <Skeleton className="h-20 w-full" />
        ) : settingQuery.isError ? (
          <Alert variant="destructive">
            <AlertTitle>无法读取生效提示词</AlertTitle>
            <AlertDescription>{resolveMemoryApiError(settingQuery.error)}</AlertDescription>
          </Alert>
        ) : (
          <div className="max-h-44 overflow-y-auto bg-muted/50 p-2">
            {settingQuery.data?.prompt ? (
              <MarkdownContent
                content={settingQuery.data.prompt}
                className="text-xs leading-relaxed"
              />
            ) : (
              <p className="text-xs leading-relaxed text-muted-foreground">
                当前层级使用系统默认提示词。
              </p>
            )}
          </div>
        )}
      </div>

      <div className="grid gap-3 min-[1200px]:grid-cols-[280px_1fr]">
        <div className="border bg-card">
          <div className="border-b px-3 py-2 text-sm font-medium">提示词资源</div>
          {promptsQuery.isLoading ? (
            <div className="space-y-2 p-3">
              {Array.from({ length: 4 }, (_, index) => (
                <Skeleton key={index} className="h-12 w-full" />
              ))}
            </div>
          ) : promptsQuery.isError ? (
            <MemoryErrorState
              message={resolveMemoryApiError(promptsQuery.error)}
              onRetry={() => promptsQuery.refetch()}
            />
          ) : promptOptions.length === 0 ? (
            <MemoryEmptyState
              title="暂无提示词资源"
              description={`${layer} 层级尚未配置可编辑的提示词资源。`}
            />
          ) : (
            <ul className="space-y-1 p-2">
              {promptOptions.map((prompt) => (
                <li key={prompt.metadata.name}>
                  <button
                    type="button"
                    onClick={() => setSelectedPromptName(prompt.metadata.name)}
                    className={`w-full px-2.5 py-2 text-left transition-colors outline-none focus-visible:ring-2 focus-visible:ring-ring/50 ${
                      prompt.metadata.name === selectedPrompt?.metadata.name
                        ? "bg-primary/5"
                        : "hover:bg-muted/60"
                    }`}
                  >
                    <span className="block truncate text-xs font-medium">
                      {prompt.spec.name || prompt.metadata.name}
                    </span>
                    <span className="block truncate font-mono text-[10px] text-muted-foreground">
                      {prompt.metadata.name}
                    </span>
                    <span className="mt-0.5 flex items-center gap-1.5 text-[11px] text-muted-foreground">
                      <Badge variant="outline">v{prompt.spec.version ?? 1}</Badge>
                      {prompt.metadata.creationTimestamp
                        ? formatUserDateTime(prompt.metadata.creationTimestamp)
                        : ""}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="border bg-card p-3">
          <div className="mb-2 flex flex-wrap items-center gap-2">
            <span className="text-sm font-medium">编辑提示词</span>
            {selectedPrompt && canManage ? (
              <div className="ms-auto flex items-center gap-1">
                <Button
                  type="button"
                  variant="ghost"
                  size="xs"
                  disabled={renameMutation.isPending || deleteMutation.isPending}
                  onClick={() => setRenameOpen(true)}
                >
                  重命名
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  size="xs"
                  disabled={renameMutation.isPending || deleteMutation.isPending}
                  onClick={() => setDeleteOpen(true)}
                >
                  删除
                </Button>
              </div>
            ) : null}
          </div>
          {detailQuery.isLoading && selectedSummary ? (
            <div className="space-y-2">
              <Skeleton className="h-9 w-full" />
              <Skeleton className="h-40 w-full" />
            </div>
          ) : selectedPrompt ? (
            <PromptEditor
              key={selectedPrompt.metadata.name}
              item={selectedPrompt}
              targetLabel={targetLabel}
              canManage={canManage}
              saving={saveMutation.isPending}
              applying={applyMutation.isPending}
              onSave={(content) => saveMutation.mutate({ item: selectedPrompt, content })}
              onApply={(content) => applyMutation.mutate({ item: selectedPrompt, content })}
            />
          ) : (
            <p className="text-xs text-muted-foreground">选择左侧提示词资源进行编辑。</p>
          )}
        </div>
      </div>

      <div className="border bg-card">
        <div className="flex items-center justify-between border-b px-3 py-2">
          <span className="text-sm font-medium">操作日志</span>
          <span className="text-[11px] text-muted-foreground">
            {selectedPrompt ? `提示词 ${selectedPrompt.metadata.name}` : "全部提示词"} · 最近 20 条
          </span>
        </div>
        {logsQuery.isLoading ? (
          <div className="space-y-2 p-3">
            {Array.from({ length: 3 }, (_, index) => (
              <Skeleton key={index} className="h-8 w-full" />
            ))}
          </div>
        ) : logsQuery.isError ? (
          <MemoryErrorState
            message={resolveMemoryApiError(logsQuery.error)}
            onRetry={() => logsQuery.refetch()}
          />
        ) : (logsQuery.data?.items.length ?? 0) === 0 ? (
          <p className="px-3 py-4 text-xs text-muted-foreground">暂无提示词操作记录。</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>时间</TableHead>
                <TableHead>提示词</TableHead>
                <TableHead>版本</TableHead>
                <TableHead>动作</TableHead>
                <TableHead>目标</TableHead>
                <TableHead>操作人</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {logsQuery.data?.items.map((log) => (
                <TableRow key={log.id}>
                  <TableCell className="text-xs text-muted-foreground">
                    {formatUserDateTime(log.createdAt ?? undefined)}
                  </TableCell>
                  <TableCell className="font-mono text-xs">{log.promptId ?? "—"}</TableCell>
                  <TableCell className="text-xs">{log.version ?? "—"}</TableCell>
                  <TableCell>
                    <Badge variant="outline">
                      {MEMORY_PROMPT_ACTION_LABELS[log.action ?? ""] ?? log.action ?? "—"}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-xs">
                    {log.targetType ? MEMORY_PROMPT_TARGET_LABELS[log.targetType] : "—"}
                    {log.team ? ` · ${log.team}` : ""}
                    {log.agent ? ` · ${log.agent}` : ""}
                  </TableCell>
                  <TableCell className="text-xs">{log.actor ?? "—"}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </div>

      {canManage ? (
        <MemoryPromptBindingsCard prompts={promptsQuery.data ?? []} currentUserId={currentUserId} />
      ) : null}

      {createOpen ? (
        <MemoryPromptCreateDialog
          pending={createOpenMutation.isPending}
          onCreate={(values) => createOpenMutation.mutate(values)}
          onClose={() => setCreateOpen(false)}
        />
      ) : null}

      {renameOpen && selectedPrompt ? (
        <MemoryPromptRenameDialog
          currentName={selectedPrompt.spec.name || selectedPrompt.metadata.name}
          pending={renameMutation.isPending}
          onRename={(displayName) =>
            renameMutation.mutate({
              name: selectedPrompt.metadata.name,
              displayName,
            })
          }
          onClose={() => setRenameOpen(false)}
        />
      ) : null}

      {deleteOpen && selectedPrompt ? (
        <MemoryConfirmDialog
          title="删除提示词"
          description={`将删除提示词「${selectedPrompt.spec.name || selectedPrompt.metadata.name}」。引用该提示词的绑定将失效并回退到上级策略。`}
          confirmLabel="删除"
          destructive
          pending={deleteMutation.isPending}
          onConfirm={() => deleteMutation.mutate(selectedPrompt.metadata.name)}
          onClose={() => setDeleteOpen(false)}
        />
      ) : null}

      {clearOpen ? (
        <MemoryConfirmDialog
          title="清除目标提示词"
          description={`将清除${MEMORY_PROMPT_TARGET_LABELS[targetType]}「${targetSuffix}」在 ${layer} 的提示词绑定，生效提示词回退到上级策略。`}
          confirmLabel="清除"
          destructive
          pending={clearMutation.isPending}
          onConfirm={() => clearMutation.mutate()}
          onClose={() => setClearOpen(false)}
        />
      ) : null}
    </div>
  );
}

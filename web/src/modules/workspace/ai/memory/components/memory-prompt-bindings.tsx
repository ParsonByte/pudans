import { useMemo, useState } from "react";
import { type Resolver, useForm, useWatch } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Ellipsis, Loader2, Plus } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { type ContentPreview, ContentPreviewDialog } from "@/components/content/content-preview";
import { FormDialog, FormDialogSection } from "@/components/form-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
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
import {
  RESOURCE_NAME_HINT,
  RESOURCE_NAME_PATTERN,
} from "@/modules/workspace/ai/providers/lib/model";

import type {
  MemoryPromptBindingItem,
  MemoryPromptItem,
  MemoryPromptLayer,
  MemoryPromptTargetType,
  ResolvedMemoryPrompt,
} from "../lib/api";
import {
  buildMemoryPromptBindingItem,
  createMemoryPromptBinding,
  deleteMemoryPromptBinding,
  fetchMemoryPromptBinding,
  patchMemoryPromptBinding,
  resolveMemoryApiError,
  resolveMemoryPrompt,
  updateMemoryPromptBinding,
} from "../lib/api";
import {
  MEMORY_PROMPT_LAYER_OPTIONS,
  MEMORY_PROMPT_SOURCE_LABELS,
  MEMORY_PROMPT_TARGET_LABELS,
  MEMORY_PROMPT_TARGET_TYPES,
} from "../lib/model";
import {
  invalidateMemoryPromptBindingQueries,
  invalidateMemoryPromptQueries,
  useMemoryPromptBindings,
} from "../lib/queries";
import { MemoryConfirmDialog } from "./memory-confirm-dialogs";
import { MemoryEmptyState, MemoryErrorState } from "./memory-states";

type MemoryPromptBindingsCardProps = {
  prompts: MemoryPromptItem[];
  currentUserId?: string;
};

const bindingSchema = z
  .object({
    name: z
      .string()
      .trim()
      .min(1, "请输入绑定名称。")
      .max(63, "绑定名称不能超过 63 个字符。")
      .regex(RESOURCE_NAME_PATTERN, RESOURCE_NAME_HINT),
    targetType: z.enum(["INSTANCE", "TEAM", "AGENT"]),
    team: z.string().trim().max(63, "团队名称过长。"),
    agent: z.string().trim().max(63, "Agent 名称过长。"),
    layer: z.enum(["L1", "L2", "L3"]),
    prompt: z.string().trim().min(1, "请选择提示词。"),
  })
  .superRefine((values, context) => {
    if (values.targetType !== "INSTANCE" && !values.team) {
      context.addIssue({
        code: "custom",
        path: ["team"],
        message: "团队或 Agent 策略必须填写团队。",
      });
    }
    if (values.targetType === "AGENT" && !values.agent) {
      context.addIssue({
        code: "custom",
        path: ["agent"],
        message: "Agent 策略必须填写 Agent。",
      });
    }
  });

type BindingFormValues = z.infer<typeof bindingSchema>;

type BindingDialogProps = {
  binding?: MemoryPromptBindingItem;
  prompts: MemoryPromptItem[];
  pending: boolean;
  onSubmit: (values: BindingFormValues) => void;
  onClose: () => void;
};

function MemoryPromptBindingDialog({
  binding,
  prompts,
  pending,
  onSubmit,
  onClose,
}: BindingDialogProps) {
  const form = useForm<BindingFormValues>({
    resolver: zodResolver(bindingSchema) as Resolver<BindingFormValues>,
    defaultValues: {
      name: binding?.metadata.name ?? "",
      targetType: binding?.spec.targetType ?? "TEAM",
      team: binding?.spec.team ?? "",
      agent: binding?.spec.agent ?? "",
      layer: binding?.spec.layer ?? "L1",
      prompt: binding?.spec.prompt ?? "",
    },
  });
  const targetType = useWatch({ control: form.control, name: "targetType" });
  const layer = useWatch({ control: form.control, name: "layer" });
  const prompt = useWatch({ control: form.control, name: "prompt" });
  const promptOptions = useMemo(
    () =>
      prompts.filter((prompt) => prompt.spec.layer === layer && prompt.spec.status !== "DELETING"),
    [prompts, layer],
  );

  return (
    <FormDialog
      open
      onClose={onClose}
      title={binding ? "编辑提示词绑定" : "新建提示词绑定"}
      description="提示词绑定按实例、团队、Agent 优先级解析，Agent 策略优先于团队与实例策略。"
      icon={<Plus />}
      form={form}
      onSubmit={form.handleSubmit(
        (values) => onSubmit(values),
        () => undefined,
      )}
      isPending={pending}
      submitLabel={binding ? "保存绑定" : "创建绑定"}
      maxHeightRatio={0.6}
    >
      <FormDialogSection title="绑定目标">
        <div className="space-y-3">
          <label className="space-y-1.5 text-sm">
            <span className="text-muted-foreground">绑定名称</span>
            <Input disabled={Boolean(binding)} {...form.register("name")} />
            {form.formState.errors.name ? (
              <p className="text-xs text-destructive">{form.formState.errors.name.message}</p>
            ) : null}
          </label>
          <label className="space-y-1.5 text-sm">
            <span className="text-muted-foreground">目标类型</span>
            <Select
              value={targetType}
              onValueChange={(value) =>
                form.setValue("targetType", value as MemoryPromptTargetType, {
                  shouldDirty: true,
                })
              }
            >
              <SelectTrigger className="w-full">
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
          </label>
          {targetType !== "INSTANCE" ? (
            <label className="space-y-1.5 text-sm">
              <span className="text-muted-foreground">团队</span>
              <Input disabled={pending} {...form.register("team")} />
              {form.formState.errors.team ? (
                <p className="text-xs text-destructive">{form.formState.errors.team.message}</p>
              ) : null}
            </label>
          ) : null}
          {targetType === "AGENT" ? (
            <label className="space-y-1.5 text-sm">
              <span className="text-muted-foreground">Agent</span>
              <Input disabled={pending} {...form.register("agent")} />
              {form.formState.errors.agent ? (
                <p className="text-xs text-destructive">{form.formState.errors.agent.message}</p>
              ) : null}
            </label>
          ) : null}
        </div>
      </FormDialogSection>
      <FormDialogSection title="提示词">
        <div className="space-y-3">
          <label className="space-y-1.5 text-sm">
            <span className="text-muted-foreground">记忆层级</span>
            <Select
              value={layer}
              onValueChange={(value) => {
                form.setValue("layer", value as MemoryPromptLayer, { shouldDirty: true });
                const nextPrompt = prompts.find(
                  (prompt) => prompt.spec.layer === value && prompt.spec.status !== "DELETING",
                );
                form.setValue("prompt", nextPrompt?.metadata.name ?? "", { shouldDirty: true });
              }}
            >
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {MEMORY_PROMPT_LAYER_OPTIONS.map((option) => (
                  <SelectItem key={option} value={option}>
                    {option}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </label>
          <label className="space-y-1.5 text-sm">
            <span className="text-muted-foreground">提示词资源</span>
            <Select
              value={prompt}
              onValueChange={(value) => form.setValue("prompt", value, { shouldDirty: true })}
            >
              <SelectTrigger className="w-full">
                <SelectValue placeholder="选择提示词" />
              </SelectTrigger>
              <SelectContent>
                {promptOptions.map((prompt) => (
                  <SelectItem key={prompt.metadata.name} value={prompt.metadata.name}>
                    {prompt.spec.name || prompt.metadata.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {promptOptions.length === 0 ? (
              <p className="text-xs text-muted-foreground">
                当前层级没有可用提示词，请先创建提示词资源。
              </p>
            ) : null}
            {form.formState.errors.prompt ? (
              <p className="text-xs text-destructive">{form.formState.errors.prompt.message}</p>
            ) : null}
          </label>
        </div>
      </FormDialogSection>
    </FormDialog>
  );
}

type SwapPromptDialogProps = {
  binding: MemoryPromptBindingItem;
  prompts: MemoryPromptItem[];
  pending: boolean;
  onSubmit: (prompt: string) => void;
  onClose: () => void;
};

function MemoryPromptSwapDialog({
  binding,
  prompts,
  pending,
  onSubmit,
  onClose,
}: SwapPromptDialogProps) {
  const options = prompts.filter(
    (prompt) => prompt.spec.layer === binding.spec.layer && prompt.spec.status !== "DELETING",
  );
  const [prompt, setPrompt] = useState(binding.spec.prompt);

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !pending) {
          onClose();
        }
      }}
    >
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>切换绑定提示词</DialogTitle>
          <DialogDescription>
            仅更新该绑定的提示词引用，目标类型、团队与层级保持不变。
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-1.5 text-sm">
          <span className="text-muted-foreground">提示词资源</span>
          <Select value={prompt} onValueChange={setPrompt}>
            <SelectTrigger className="w-full">
              <SelectValue placeholder="选择提示词" />
            </SelectTrigger>
            <SelectContent>
              {options.map((item) => (
                <SelectItem key={item.metadata.name} value={item.metadata.name}>
                  {item.spec.name || item.metadata.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onClose} disabled={pending}>
            取消
          </Button>
          <Button
            type="button"
            disabled={!prompt || prompt === binding.spec.prompt || pending}
            onClick={() => onSubmit(prompt)}
          >
            {pending ? <Loader2 className="animate-spin" /> : null}
            切换提示词
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export function MemoryPromptBindingsCard({
  prompts,
  currentUserId,
}: MemoryPromptBindingsCardProps) {
  const queryClient = useQueryClient();
  const [dialog, setDialog] = useState<
    { mode: "create" } | { mode: "edit"; binding: MemoryPromptBindingItem } | null
  >(null);
  const [deleteTarget, setDeleteTarget] = useState<MemoryPromptBindingItem | null>(null);
  const [swapTarget, setSwapTarget] = useState<MemoryPromptBindingItem | null>(null);
  const [preview, setPreview] = useState<ContentPreview | null>(null);
  const [previewing, setPreviewing] = useState<string | null>(null);

  const query = useMemoryPromptBindings();
  const bindings = query.data ?? [];
  const promptLabels = useMemo(
    () =>
      new Map(
        prompts.map((prompt) => [prompt.metadata.name, prompt.spec.name || prompt.metadata.name]),
      ),
    [prompts],
  );

  const invalidate = async () => {
    await invalidateMemoryPromptBindingQueries(queryClient);
    await invalidateMemoryPromptQueries(queryClient);
  };

  const saveMutation = useMutation({
    mutationFn: (input: {
      values: BindingFormValues;
      existing?: MemoryPromptBindingItem;
      bindingName: string;
    }) => {
      const item = buildMemoryPromptBindingItem(
        {
          name: input.bindingName,
          targetType: input.values.targetType,
          team: input.values.team || undefined,
          agent: input.values.agent || undefined,
          layer: input.values.layer,
          prompt: input.values.prompt,
        },
        input.existing,
      );
      return input.existing ? updateMemoryPromptBinding(item) : createMemoryPromptBinding(item);
    },
    onSuccess: async (_, input) => {
      await invalidate();
      toast.success(input.existing ? "提示词绑定已保存" : "提示词绑定已创建");
      setDialog(null);
    },
    onError: (error) => toast.error(resolveMemoryApiError(error)),
  });

  const swapMutation = useMutation({
    mutationFn: (input: { binding: MemoryPromptBindingItem; prompt: string }) =>
      patchMemoryPromptBinding(input.binding.metadata.name, [
        { op: "replace", path: "/spec/prompt", value: input.prompt },
        ...(currentUserId
          ? [{ op: "add" as const, path: "/spec/updatedBy", value: currentUserId }]
          : []),
      ]),
    onSuccess: async () => {
      await invalidate();
      toast.success("绑定提示词已切换");
      setSwapTarget(null);
    },
    onError: (error) => toast.error(resolveMemoryApiError(error)),
  });

  const deleteMutation = useMutation({
    mutationFn: (binding: MemoryPromptBindingItem) =>
      deleteMemoryPromptBinding(binding.metadata.name),
    onSuccess: async () => {
      await invalidate();
      toast.success("提示词绑定已删除");
      setDeleteTarget(null);
    },
    onError: (error) => toast.error(resolveMemoryApiError(error)),
  });

  async function previewBinding(binding: MemoryPromptBindingItem) {
    setPreviewing(binding.metadata.name);
    try {
      const detail = await fetchMemoryPromptBinding(binding.metadata.name);
      setPreview({
        title: `提示词绑定 · ${detail.metadata.name}`,
        description: `${MEMORY_PROMPT_TARGET_LABELS[detail.spec.targetType]}${
          detail.spec.team ? ` · ${detail.spec.team}` : ""
        }${detail.spec.agent ? ` · ${detail.spec.agent}` : ""} · ${detail.spec.layer}`,
        content: [
          `**提示词**\n\n${detail.spec.prompt}`,
          detail.spec.updatedBy ? `**更新人**\n\n${detail.spec.updatedBy}` : "",
        ]
          .filter(Boolean)
          .join("\n\n"),
      });
    } catch (error) {
      toast.error(resolveMemoryApiError(error));
    } finally {
      setPreviewing(null);
    }
  }

  async function resolveBinding(binding: MemoryPromptBindingItem) {
    setPreviewing(binding.metadata.name);
    try {
      const resolved = await resolveMemoryPrompt({
        layer: binding.spec.layer,
        team: binding.spec.targetType === "INSTANCE" ? undefined : (binding.spec.team ?? undefined),
        agent: binding.spec.targetType === "AGENT" ? (binding.spec.agent ?? undefined) : undefined,
      });
      setPreview({
        title: `生效提示词 · ${binding.spec.layer}`,
        description: `${MEMORY_PROMPT_SOURCE_LABELS[resolved.source] ?? resolved.source} · v${resolved.version}`,
        content: promptPreviewContent(resolved),
      });
    } catch (error) {
      toast.error(resolveMemoryApiError(error));
    } finally {
      setPreviewing(null);
    }
  }

  return (
    <div className="border bg-card">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b px-3 py-2">
        <div className="space-y-0.5">
          <h3 className="text-sm font-medium">提示词绑定</h3>
          <p className="text-[11px] text-muted-foreground">
            实例、团队与 Agent 策略按优先级解析；通过「应用提示词」产生的变更会写入操作日志。
          </p>
        </div>
        <div className="flex items-center gap-1">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            disabled={query.isFetching}
            onClick={() => query.refetch()}
          >
            刷新
          </Button>
          <Button type="button" size="sm" onClick={() => setDialog({ mode: "create" })}>
            <Plus />
            新建绑定
          </Button>
        </div>
      </div>

      {query.isLoading ? (
        <div className="space-y-2 p-3">
          {Array.from({ length: 3 }, (_, index) => (
            <Skeleton key={index} className="h-9 w-full" />
          ))}
        </div>
      ) : query.isError ? (
        <MemoryErrorState
          title="无法加载提示词绑定"
          message={resolveMemoryApiError(query.error)}
          onRetry={() => query.refetch()}
        />
      ) : bindings.length === 0 ? (
        <MemoryEmptyState
          title="暂无提示词绑定"
          description="应用提示词后会自动生成绑定，也可在此手动创建。"
        />
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>绑定</TableHead>
              <TableHead>目标</TableHead>
              <TableHead>层级</TableHead>
              <TableHead>提示词</TableHead>
              <TableHead>更新人</TableHead>
              <TableHead className="text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {bindings.map((binding) => (
              <TableRow key={binding.metadata.name}>
                <TableCell className="font-mono text-xs">{binding.metadata.name}</TableCell>
                <TableCell className="text-xs">
                  <Badge variant="secondary">
                    {MEMORY_PROMPT_TARGET_LABELS[binding.spec.targetType] ??
                      binding.spec.targetType}
                  </Badge>
                  <p className="mt-1 text-[11px] text-muted-foreground">
                    {binding.spec.team ?? "实例"}{" "}
                    {binding.spec.agent ? `· ${binding.spec.agent}` : ""}
                  </p>
                </TableCell>
                <TableCell>
                  <Badge variant="outline">{binding.spec.layer}</Badge>
                </TableCell>
                <TableCell className="text-xs">
                  {promptLabels.get(binding.spec.prompt) ?? binding.spec.prompt}
                </TableCell>
                <TableCell className="text-xs text-muted-foreground">
                  {binding.spec.updatedBy ?? "—"}
                </TableCell>
                <TableCell className="text-right">
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon-sm"
                        aria-label="提示词绑定操作"
                        disabled={previewing === binding.metadata.name}
                      >
                        <Ellipsis />
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end" className="w-44">
                      <DropdownMenuItem onSelect={() => void resolveBinding(binding)}>
                        解析生效提示词
                      </DropdownMenuItem>
                      <DropdownMenuItem onSelect={() => void previewBinding(binding)}>
                        查看绑定详情
                      </DropdownMenuItem>
                      <DropdownMenuItem onSelect={() => setDialog({ mode: "edit", binding })}>
                        编辑绑定
                      </DropdownMenuItem>
                      <DropdownMenuItem onSelect={() => setSwapTarget(binding)}>
                        切换提示词
                      </DropdownMenuItem>
                      <DropdownMenuSeparator />
                      <DropdownMenuItem
                        variant="destructive"
                        onSelect={() => setDeleteTarget(binding)}
                      >
                        删除绑定
                      </DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <ContentPreviewDialog preview={preview} onClose={() => setPreview(null)} />

      {dialog ? (
        <MemoryPromptBindingDialog
          key={dialog.mode === "edit" ? dialog.binding.metadata.name : "create"}
          binding={dialog.mode === "edit" ? dialog.binding : undefined}
          prompts={prompts}
          pending={saveMutation.isPending}
          onSubmit={(values) =>
            saveMutation.mutate({
              values,
              bindingName: dialog.mode === "edit" ? dialog.binding.metadata.name : values.name,
              existing: dialog.mode === "edit" ? dialog.binding : undefined,
            })
          }
          onClose={() => setDialog(null)}
        />
      ) : null}

      {swapTarget ? (
        <MemoryPromptSwapDialog
          binding={swapTarget}
          prompts={prompts}
          pending={swapMutation.isPending}
          onSubmit={(prompt) => swapMutation.mutate({ binding: swapTarget, prompt })}
          onClose={() => setSwapTarget(null)}
        />
      ) : null}

      {deleteTarget ? (
        <MemoryConfirmDialog
          title="删除提示词绑定"
          description={`将删除绑定「${deleteTarget.metadata.name}」，目标将回退到上级或系统默认提示词。`}
          confirmLabel="删除"
          destructive
          pending={deleteMutation.isPending}
          onConfirm={() => deleteMutation.mutate(deleteTarget)}
          onClose={() => setDeleteTarget(null)}
        />
      ) : null}
    </div>
  );
}

function promptPreviewContent(resolved: ResolvedMemoryPrompt): string {
  if (!resolved.prompt) {
    return "当前层级使用系统默认提示词。";
  }
  return `来源：${resolved.source} · v${resolved.version}\n\n${resolved.prompt}`;
}

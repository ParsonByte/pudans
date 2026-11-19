import { useState } from "react";
import { type Resolver, useForm, useWatch } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ChevronDown,
  ChevronRight,
  Loader2,
  Pencil,
  Plus,
  RefreshCw,
  Trash2,
  X,
  Zap,
} from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { AutoScrollArea } from "@/components/auto-scroll-area";
import { ConfirmDialog } from "@/components/confirm-dialog";
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
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { useAllowedOperations } from "@/modules/workspace/lib/permissions";

import {
  AI_API_VERSION,
  AI_MODEL_KIND,
  AI_PROVIDER_ACCOUNT_KIND,
  type AiModelItem,
  type AiProviderAccountItem,
  type AiProviderAffinity,
  type AiProviderItem,
  type AiProviderRouting,
  type AiRemoteModel,
  createModel,
  createProviderAccount,
  deleteModel,
  deleteProvider,
  deleteProviderAccount,
  fetchProvider,
  fetchProviderAccounts,
  fetchProviderModels,
  fetchProviderRemoteModels,
  patchProviderAccount,
  resolveAiProviderApiError,
  testModel,
  testProvider,
  testProviderAccount,
  updateModel,
  updateProvider,
} from "../lib/api";
import {
  AFFINITY_OPTIONS,
  DEFAULT_PROVIDER_SPEC,
  PROTOCOL_OPTIONS,
  ROUTING_OPTIONS,
  formatBalance,
  formatQuota,
  formatTestResult,
  modelNameFromCode,
  optionLabel,
  providerEndpoints,
  supportsModelDiscovery,
  uniqueModelName,
} from "../lib/model";
import { AccountDialog } from "./account-dialog";
import { ModelDialog } from "./model-dialog";
import { QuotaDialog } from "./quota-dialog";

const ROUTING_VALUES = ROUTING_OPTIONS.map((option) => option.value) as [
  AiProviderRouting,
  ...AiProviderRouting[],
];

const AFFINITY_VALUES = AFFINITY_OPTIONS.map((option) => option.value) as [
  AiProviderAffinity,
  ...AiProviderAffinity[],
];

const editorSchema = z.object({
  displayName: z.string().trim().min(1, "请输入名称。").max(100, "名称不能超过 100 个字符。"),
  routing: z.enum(ROUTING_VALUES),
  affinity: z.enum(AFFINITY_VALUES),
  maxConcurrency: z.coerce
    .number()
    .int()
    .min(1, "并发数至少为 1。")
    .max(100000, "并发数不能超过 100000。"),
  maxRPM: z.coerce.number().int().min(0, "每分钟请求数不能为负数。").max(1000000),
  connectTimeoutMs: z.coerce.number().int().min(100, "连接超时至少为 100 毫秒。").max(600000),
  readTimeoutMs: z.coerce.number().int().min(100, "读取超时至少为 100 毫秒。").max(3600000),
  free: z.boolean(),
});

type EditorFormValues = z.infer<typeof editorSchema>;

type ProviderEditorDialogProps = {
  provider: AiProviderItem;
  onClose: () => void;
};

export function ProviderEditorDialog({
  provider: initialProvider,
  onClose,
}: ProviderEditorDialogProps) {
  const queryClient = useQueryClient();
  const allowed = useAllowedOperations();
  const canCreate = allowed.has("create");
  const canUpdate = allowed.has("update");
  const canDelete = allowed.has("delete");
  const canTest = allowed.has("test");
  const name = initialProvider.metadata.name;

  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [quickKeyOpen, setQuickKeyOpen] = useState(false);
  const [quickKeyName, setQuickKeyName] = useState("");
  const [quickKeyValue, setQuickKeyValue] = useState("");
  const [modelCode, setModelCode] = useState("");
  const [accountTarget, setAccountTarget] = useState<AiProviderAccountItem | null | undefined>(
    undefined,
  );
  const [quotaTarget, setQuotaTarget] = useState<AiProviderAccountItem | null>(null);
  const [modelTarget, setModelTarget] = useState<AiModelItem | null>(null);
  const [deleteProviderOpen, setDeleteProviderOpen] = useState(false);
  const [deleteAccountTarget, setDeleteAccountTarget] = useState<AiProviderAccountItem | null>(
    null,
  );
  const [deleteModelTarget, setDeleteModelTarget] = useState<AiModelItem | null>(null);

  const providerQuery = useQuery({
    queryKey: ["ai-provider", name],
    queryFn: () => fetchProvider(name),
    initialData: initialProvider,
    retry: false,
  });
  const accountsQuery = useQuery({
    queryKey: ["ai-provider-accounts", name],
    queryFn: () => fetchProviderAccounts(name),
    retry: false,
  });
  const modelsQuery = useQuery({
    queryKey: ["ai-provider-models", name],
    queryFn: () => fetchProviderModels(name),
    retry: false,
  });
  const provider = providerQuery.data ?? initialProvider;
  const accounts = accountsQuery.data ?? [];
  const models = modelsQuery.data ?? [];
  const remoteEnabled = supportsModelDiscovery(provider.spec.protocol);
  const remoteQuery = useQuery({
    queryKey: ["ai-provider-remote-models", name, provider.spec.protocol],
    queryFn: () => fetchProviderRemoteModels(name),
    enabled: remoteEnabled && accounts.length > 0,
    retry: false,
  });
  const remoteModels = remoteQuery.data ?? [];
  const existingCodes = new Set(models.map((model) => model.spec.modelCode));
  const importedCandidates = remoteModels.filter((model) => !existingCodes.has(model.id));
  const endpoints = providerEndpoints(provider.spec.protocol, provider.spec.baseUrl);

  const form = useForm<EditorFormValues>({
    resolver: zodResolver(editorSchema) as Resolver<EditorFormValues>,
    defaultValues: {
      displayName: provider.spec.displayName || name,
      routing: provider.spec.routing ?? DEFAULT_PROVIDER_SPEC.routing,
      affinity: provider.spec.affinity ?? DEFAULT_PROVIDER_SPEC.affinity,
      maxConcurrency: provider.spec.maxConcurrency ?? DEFAULT_PROVIDER_SPEC.maxConcurrency,
      maxRPM: provider.spec.maxRPM ?? DEFAULT_PROVIDER_SPEC.maxRPM,
      connectTimeoutMs: provider.spec.connectTimeoutMs ?? DEFAULT_PROVIDER_SPEC.connectTimeoutMs,
      readTimeoutMs: provider.spec.readTimeoutMs ?? DEFAULT_PROVIDER_SPEC.readTimeoutMs,
      free: provider.spec.free ?? false,
    },
  });
  const free = useWatch({ control: form.control, name: "free" });
  const routing = useWatch({ control: form.control, name: "routing" });
  const affinity = useWatch({ control: form.control, name: "affinity" });

  const invalidateProvider = () =>
    queryClient.invalidateQueries({ queryKey: ["ai-provider", name] });
  const invalidateAccounts = () =>
    queryClient.invalidateQueries({ queryKey: ["ai-provider-accounts"] });
  const invalidateModels = () =>
    queryClient.invalidateQueries({ queryKey: ["ai-provider-models"] });
  const invalidateList = () => queryClient.invalidateQueries({ queryKey: ["ai-providers"] });

  const saveMutation = useMutation({
    mutationFn: (values: EditorFormValues) =>
      updateProvider({
        ...provider,
        spec: {
          ...provider.spec,
          displayName: values.displayName,
          routing: values.routing,
          affinity: values.affinity,
          maxConcurrency: values.maxConcurrency,
          maxRPM: values.maxRPM,
          connectTimeoutMs: values.connectTimeoutMs,
          readTimeoutMs: values.readTimeoutMs,
          free: values.free,
        },
      }),
    onSuccess: async () => {
      await invalidateProvider();
      await invalidateList();
      toast.success("供应商已保存。");
      onClose();
    },
    onError: (error) => toast.error(resolveAiProviderApiError(error)),
  });

  const toggleProviderMutation = useMutation({
    mutationFn: () =>
      updateProvider({
        ...provider,
        spec: { ...provider.spec, enabled: provider.spec.enabled === false },
      }),
    onSuccess: async () => {
      await invalidateProvider();
      await invalidateList();
    },
    onError: (error) => toast.error(resolveAiProviderApiError(error)),
  });

  const quickKeyMutation = useMutation({
    mutationFn: (apiKey: string) =>
      createProviderAccount({
        apiVersion: AI_API_VERSION,
        kind: AI_PROVIDER_ACCOUNT_KIND,
        metadata: { name: `${name.slice(0, 40)}-${Date.now().toString(36)}` },
        spec: {
          provider: name,
          displayName: quickKeyName.trim() || undefined,
          accountType: "API_KEY",
          apiKey,
          weight: 1,
          priority: 0,
          enabled: true,
          freePlan: false,
          creditsEnabled: false,
        },
      }),
    onSuccess: async () => {
      await invalidateAccounts();
      await invalidateList();
      setQuickKeyName("");
      setQuickKeyValue("");
      setQuickKeyOpen(false);
      toast.success("密钥已添加。");
    },
    onError: (error) => toast.error(resolveAiProviderApiError(error)),
  });

  const addQuickKey = () => {
    const apiKey = quickKeyValue.trim();
    if (!apiKey) {
      toast.error("请粘贴 API 密钥。");
      return;
    }
    quickKeyMutation.mutate(apiKey);
  };

  const toggleAccountMutation = useMutation({
    mutationFn: (account: AiProviderAccountItem) =>
      patchProviderAccount(account.metadata.name, [
        { op: "add", path: "/spec/enabled", value: !account.spec.enabled },
      ]),
    onSuccess: async () => {
      await invalidateAccounts();
      await invalidateList();
    },
    onError: (error) => toast.error(resolveAiProviderApiError(error)),
  });

  const deleteAccountMutation = useMutation({
    mutationFn: (account: AiProviderAccountItem) => deleteProviderAccount(account.metadata.name),
    onSuccess: async () => {
      await invalidateAccounts();
      await invalidateList();
      setDeleteAccountTarget(null);
      toast.success("密钥已删除。");
    },
    onError: (error) => {
      setDeleteAccountTarget(null);
      toast.error(resolveAiProviderApiError(error));
    },
  });

  const testAccountMutation = useMutation({
    mutationFn: (account: AiProviderAccountItem) => testProviderAccount(account.metadata.name),
    onSuccess: (result) => toast.success(formatTestResult(result)),
    onError: (error) => toast.error(resolveAiProviderApiError(error)),
  });

  const importModelMutation = useMutation({
    mutationFn: (remote: AiRemoteModel) => {
      const usedNames = new Set(models.map((model) => model.metadata.name));
      return createModel({
        apiVersion: AI_API_VERSION,
        kind: AI_MODEL_KIND,
        metadata: { name: uniqueModelName(modelNameFromCode(remote.id), usedNames) },
        spec: {
          provider: name,
          modelCode: remote.id,
          displayName: remote.name || remote.id,
          modelType: "CHAT",
          contextWindow: remote.contextWindow,
          maxOutputTokens: remote.maxOutputTokens,
          supportsStream: true,
          enabled: true,
        },
      });
    },
    onSuccess: async () => {
      await invalidateModels();
      await invalidateList();
      toast.success("模型已添加。");
    },
    onError: (error) => toast.error(resolveAiProviderApiError(error)),
  });

  const quickModelMutation = useMutation({
    mutationFn: (code: string) => {
      const usedNames = new Set(models.map((model) => model.metadata.name));
      return createModel({
        apiVersion: AI_API_VERSION,
        kind: AI_MODEL_KIND,
        metadata: { name: uniqueModelName(modelNameFromCode(code), usedNames) },
        spec: {
          provider: name,
          modelCode: code,
          displayName: code,
          modelType: "CHAT",
          supportsStream: true,
          enabled: true,
        },
      });
    },
    onSuccess: async () => {
      await invalidateModels();
      await invalidateList();
      setModelCode("");
      toast.success("模型已添加。");
    },
    onError: (error) => toast.error(resolveAiProviderApiError(error)),
  });

  const toggleModelMutation = useMutation({
    mutationFn: (model: AiModelItem) =>
      updateModel({
        ...model,
        spec: { ...model.spec, enabled: model.spec.enabled === false },
      }),
    onSuccess: async () => {
      await invalidateModels();
    },
    onError: (error) => toast.error(resolveAiProviderApiError(error)),
  });

  const bulkModelMutation = useMutation({
    mutationFn: (enabled: boolean) =>
      Promise.allSettled(
        models.map((model) => updateModel({ ...model, spec: { ...model.spec, enabled } })),
      ),
    onSuccess: async (results) => {
      await invalidateModels();
      const failures = results.filter((result) => result.status === "rejected").length;
      if (failures > 0) {
        toast.error(`${failures} 个模型更新失败。`);
      }
    },
    onError: (error) => toast.error(resolveAiProviderApiError(error)),
  });

  const deleteModelMutation = useMutation({
    mutationFn: (model: AiModelItem) => deleteModel(model.metadata.name),
    onSuccess: async () => {
      await invalidateModels();
      await invalidateList();
      setDeleteModelTarget(null);
      toast.success("模型已删除。");
    },
    onError: (error) => {
      setDeleteModelTarget(null);
      toast.error(resolveAiProviderApiError(error));
    },
  });

  const testModelMutation = useMutation({
    mutationFn: (model: AiModelItem) => testModel(model.metadata.name),
    onSuccess: (result) => toast.success(formatTestResult(result)),
    onError: (error) => toast.error(resolveAiProviderApiError(error)),
  });

  const testProviderMutation = useMutation({
    mutationFn: () => testProvider(name),
    onSuccess: (result) => toast.success(formatTestResult(result)),
    onError: (error) => toast.error(resolveAiProviderApiError(error)),
  });

  const deleteProviderMutation = useMutation({
    mutationFn: () => deleteProvider(name),
    onSuccess: async () => {
      await invalidateList();
      await queryClient.invalidateQueries({ queryKey: ["ai-provider-accounts"] });
      await queryClient.invalidateQueries({ queryKey: ["ai-provider-models"] });
      setDeleteProviderOpen(false);
      toast.success("供应商已删除。");
      onClose();
    },
    onError: (error) => {
      setDeleteProviderOpen(false);
      toast.error(resolveAiProviderApiError(error));
    },
  });

  const onSubmit = form.handleSubmit(
    (values) => saveMutation.mutate(values),
    () => toast.error("请检查填写内容。"),
  );

  const firstTestableModel = models.find((model) => model.spec.enabled !== false) ?? models[0];

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="flex max-h-[90vh] flex-col overflow-hidden sm:max-w-3xl">
        <DialogHeader>
          <div className="flex items-start justify-between gap-4 pe-8">
            <div className="min-w-0 space-y-1">
              <DialogTitle className="flex items-center gap-2">
                <span className="truncate">{provider.spec.displayName || name}</span>
                <Badge variant={provider.spec.enabled === false ? "outline" : "secondary"}>
                  {provider.spec.enabled === false ? "已停用" : "已启用"}
                </Badge>
              </DialogTitle>
              <DialogDescription>
                {provider.spec.baseUrl ? (
                  <a href={provider.spec.baseUrl} target="_blank" rel="noreferrer">
                    {provider.spec.baseUrl}
                  </a>
                ) : (
                  "未配置接入地址"
                )}
              </DialogDescription>
            </div>
            <div className="flex items-center gap-2">
              {toggleProviderMutation.isPending ? (
                <Loader2 className="size-4 animate-spin text-muted-foreground" />
              ) : null}
              <Switch
                checked={provider.spec.enabled !== false}
                disabled={!canUpdate || toggleProviderMutation.isPending}
                onCheckedChange={() => toggleProviderMutation.mutate()}
                aria-label="启用供应商"
              />
            </div>
          </div>
        </DialogHeader>

        <AutoScrollArea maxHeightRatio={0.72} className="min-h-0 flex-1">
          <div className="space-y-6 pb-2">
            <section className="space-y-4">
              <div className="grid gap-4 sm:grid-cols-2">
                <label className="space-y-1 text-sm">
                  <span className="text-xs text-muted-foreground">ID</span>
                  <Input value={name} disabled />
                </label>
                <label className="space-y-1 text-sm">
                  <span className="text-xs text-muted-foreground">名称</span>
                  <Input
                    disabled={!canUpdate}
                    {...form.register("displayName")}
                    aria-invalid={Boolean(form.formState.errors.displayName)}
                  />
                  {form.formState.errors.displayName ? (
                    <span className="text-xs text-destructive">
                      {form.formState.errors.displayName.message}
                    </span>
                  ) : null}
                </label>
              </div>
            </section>

            <section className="space-y-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <h3 className="text-sm font-semibold">账号</h3>
                  <p className="text-xs text-muted-foreground">
                    {accounts.length} 个密钥用于实际调用供应商接口。
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  {canCreate ? (
                    <>
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        onClick={() => setQuickKeyOpen((open) => !open)}
                      >
                        <Plus />
                        添加另一个密钥
                      </Button>
                      <Button type="button" size="sm" onClick={() => setAccountTarget(null)}>
                        更多设置
                      </Button>
                    </>
                  ) : null}
                </div>
              </div>

              {quickKeyOpen && canCreate ? (
                <div className="flex flex-wrap items-center gap-2 rounded-lg border p-3">
                  <Input
                    value={quickKeyName}
                    onChange={(event) => setQuickKeyName(event.target.value)}
                    placeholder="名称（可选）"
                    className="w-40"
                  />
                  <Input
                    type="password"
                    autoComplete="new-password"
                    value={quickKeyValue}
                    onChange={(event) => setQuickKeyValue(event.target.value)}
                    placeholder="粘贴 API 密钥"
                    className="min-w-56 flex-1"
                  />
                  <Button
                    type="button"
                    size="sm"
                    disabled={quickKeyMutation.isPending}
                    onClick={addQuickKey}
                  >
                    {quickKeyMutation.isPending ? <Loader2 className="animate-spin" /> : null}
                    添加
                  </Button>
                  <Button
                    type="button"
                    size="sm"
                    variant="ghost"
                    onClick={() => setQuickKeyOpen(false)}
                  >
                    取消
                  </Button>
                </div>
              ) : null}

              {accountsQuery.isPending ? (
                <p className="text-sm text-muted-foreground">正在加载账号…</p>
              ) : accountsQuery.isError ? (
                <p className="text-sm text-destructive">
                  {resolveAiProviderApiError(accountsQuery.error)}
                </p>
              ) : accounts.length === 0 ? (
                <p className="rounded-lg border border-dashed p-4 text-sm text-muted-foreground">
                  还没有密钥。至少添加一个密钥后才能测试供应商。
                </p>
              ) : (
                <div className="space-y-2">
                  {accounts.map((account) => (
                    <div
                      key={account.metadata.name}
                      className="flex flex-wrap items-center gap-3 rounded-lg border p-3"
                    >
                      <Switch
                        checked={account.spec.enabled}
                        disabled={!canUpdate || toggleAccountMutation.isPending}
                        onCheckedChange={() => toggleAccountMutation.mutate(account)}
                        aria-label={account.spec.enabled ? "停用密钥" : "启用密钥"}
                      />
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium">
                          {account.spec.displayName || account.metadata.name}
                        </p>
                        <p className="truncate text-xs text-muted-foreground">
                          密钥已配置（不可查看） ·{" "}
                          {optionLabel(PROTOCOL_OPTIONS, provider.spec.protocol)}
                        </p>
                      </div>
                      <div className="flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground">
                        <Badge variant="outline">权重 {account.spec.weight}</Badge>
                        <span>{formatQuota(account.spec.quota)}</span>
                        <span>{formatBalance(account.spec.balance)}</span>
                      </div>
                      <div className="flex items-center gap-1">
                        {canTest ? (
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            disabled={testAccountMutation.isPending}
                            onClick={() => testAccountMutation.mutate(account)}
                          >
                            {testAccountMutation.isPending ? (
                              <Loader2 className="animate-spin" />
                            ) : (
                              <Zap />
                            )}
                            测试
                          </Button>
                        ) : null}
                        {canUpdate ? (
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            onClick={() => setQuotaTarget(account)}
                          >
                            配额
                          </Button>
                        ) : null}
                        {canUpdate ? (
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            onClick={() => setAccountTarget(account)}
                          >
                            <Pencil />
                            编辑
                          </Button>
                        ) : null}
                        {canDelete ? (
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            className="text-destructive hover:text-destructive"
                            onClick={() => setDeleteAccountTarget(account)}
                          >
                            <Trash2 />
                          </Button>
                        ) : null}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </section>

            <section className="space-y-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <h3 className="text-sm font-semibold">模型</h3>
                  <p className="text-xs text-muted-foreground">
                    {models.length} 个已导入模型；虚线为供应商返回但尚未导入的模型。
                  </p>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  {canUpdate ? (
                    <>
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        disabled={bulkModelMutation.isPending || models.length === 0}
                        onClick={() => bulkModelMutation.mutate(true)}
                      >
                        全选
                      </Button>
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        disabled={bulkModelMutation.isPending || models.length === 0}
                        onClick={() => bulkModelMutation.mutate(false)}
                      >
                        全不选
                      </Button>
                    </>
                  ) : null}
                  {remoteEnabled ? (
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      disabled={remoteQuery.isFetching}
                      onClick={() => void remoteQuery.refetch()}
                    >
                      {remoteQuery.isFetching ? (
                        <Loader2 className="animate-spin" />
                      ) : (
                        <RefreshCw />
                      )}
                      重新获取模型
                    </Button>
                  ) : null}
                  {canTest ? (
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      disabled={testModelMutation.isPending || !firstTestableModel}
                      onClick={() => {
                        if (firstTestableModel) {
                          testModelMutation.mutate(firstTestableModel);
                        }
                      }}
                    >
                      {testModelMutation.isPending ? <Loader2 className="animate-spin" /> : <Zap />}
                      测试模型
                    </Button>
                  ) : null}
                </div>
              </div>

              {remoteQuery.isError ? (
                <p className="text-xs text-muted-foreground">
                  无法获取供应商模型列表：{resolveAiProviderApiError(remoteQuery.error)}
                </p>
              ) : null}

              <div className="flex flex-wrap gap-1.5">
                {models.map((model) => (
                  <div
                    key={model.metadata.name}
                    className={`flex items-center gap-1 rounded-full border px-3 py-1 text-xs ${
                      model.spec.enabled === false
                        ? "border-dashed text-muted-foreground"
                        : "bg-muted/40"
                    }`}
                  >
                    <button
                      type="button"
                      className="max-w-48 truncate"
                      onClick={() => setModelTarget(model)}
                    >
                      {model.spec.modelCode}
                    </button>
                    {canUpdate ? (
                      <button
                        type="button"
                        className="text-muted-foreground hover:text-foreground"
                        aria-label={model.spec.enabled === false ? "启用模型" : "停用模型"}
                        onClick={() => toggleModelMutation.mutate(model)}
                      >
                        <span className="block size-1.5 rounded-full bg-current" />
                      </button>
                    ) : null}
                    {canDelete ? (
                      <button
                        type="button"
                        className="text-muted-foreground hover:text-destructive"
                        aria-label="删除模型"
                        onClick={() => setDeleteModelTarget(model)}
                      >
                        <X className="size-3" />
                      </button>
                    ) : null}
                  </div>
                ))}
                {importedCandidates.map((remote) => (
                  <button
                    key={remote.id}
                    type="button"
                    disabled={!canCreate || importModelMutation.isPending}
                    className="rounded-full border border-dashed px-3 py-1 text-xs text-muted-foreground transition-colors hover:border-primary/50 hover:text-foreground disabled:opacity-50"
                    onClick={() => importModelMutation.mutate(remote)}
                  >
                    <Plus className="me-1 inline size-3" />
                    {remote.id}
                  </button>
                ))}
              </div>

              {canCreate ? (
                <div className="flex flex-wrap items-center gap-2">
                  <Input
                    value={modelCode}
                    onChange={(event) => setModelCode(event.target.value)}
                    placeholder="添加模型 ID，例如 deepseek-chat"
                    className="w-64"
                  />
                  <Button
                    type="button"
                    size="sm"
                    disabled={quickModelMutation.isPending}
                    onClick={() => {
                      const code = modelCode.trim();
                      if (!code) {
                        toast.error("请输入模型 ID。");
                        return;
                      }
                      quickModelMutation.mutate(code);
                    }}
                  >
                    {quickModelMutation.isPending ? <Loader2 className="animate-spin" /> : null}
                    添加模型
                  </Button>
                </div>
              ) : null}
            </section>

            <section className="space-y-2">
              <h3 className="text-sm font-semibold">端点</h3>
              {endpoints.length === 0 ? (
                <p className="text-sm text-muted-foreground">配置接入地址后显示端点。</p>
              ) : (
                <div className="space-y-2">
                  {endpoints.map((endpoint) => (
                    <div
                      key={endpoint.label}
                      className="flex flex-wrap items-center justify-between gap-2 text-sm"
                    >
                      <span className="text-muted-foreground">{endpoint.label}</span>
                      <code className="rounded bg-muted px-2 py-1 text-xs">{endpoint.url}</code>
                    </div>
                  ))}
                </div>
              )}
              {canTest ? (
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  disabled={testProviderMutation.isPending}
                  onClick={() => testProviderMutation.mutate()}
                >
                  {testProviderMutation.isPending ? <Loader2 className="animate-spin" /> : <Zap />}
                  测试供应商
                </Button>
              ) : null}
            </section>

            <section className="space-y-3">
              <button
                type="button"
                className="flex items-center gap-1 text-sm font-semibold"
                onClick={() => setAdvancedOpen((open) => !open)}
              >
                {advancedOpen ? (
                  <ChevronDown className="size-4" />
                ) : (
                  <ChevronRight className="size-4" />
                )}
                高级
              </button>
              {advancedOpen ? (
                <div className="grid gap-4 sm:grid-cols-2">
                  <label className="space-y-1 text-sm">
                    <span className="text-xs text-muted-foreground">路由策略</span>
                    <Select
                      value={routing}
                      onValueChange={(value) =>
                        form.setValue("routing", value as AiProviderRouting, {
                          shouldDirty: true,
                        })
                      }
                      disabled={!canUpdate}
                    >
                      <SelectTrigger className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {ROUTING_OPTIONS.map((option) => (
                          <SelectItem key={option.value} value={option.value}>
                            {option.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </label>
                  <label className="space-y-1 text-sm">
                    <span className="text-xs text-muted-foreground">亲和策略</span>
                    <Select
                      value={affinity}
                      onValueChange={(value) =>
                        form.setValue("affinity", value as AiProviderAffinity, {
                          shouldDirty: true,
                        })
                      }
                      disabled={!canUpdate}
                    >
                      <SelectTrigger className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {AFFINITY_OPTIONS.map((option) => (
                          <SelectItem key={option.value} value={option.value}>
                            {option.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </label>
                  <label className="space-y-1 text-sm">
                    <span className="text-xs text-muted-foreground">最大并发</span>
                    <Input
                      type="number"
                      min={1}
                      disabled={!canUpdate}
                      {...form.register("maxConcurrency")}
                    />
                  </label>
                  <label className="space-y-1 text-sm">
                    <span className="text-xs text-muted-foreground">每分钟请求上限</span>
                    <Input
                      type="number"
                      min={0}
                      disabled={!canUpdate}
                      {...form.register("maxRPM")}
                    />
                  </label>
                  <label className="space-y-1 text-sm">
                    <span className="text-xs text-muted-foreground">连接超时（毫秒）</span>
                    <Input
                      type="number"
                      min={100}
                      disabled={!canUpdate}
                      {...form.register("connectTimeoutMs")}
                    />
                  </label>
                  <label className="space-y-1 text-sm">
                    <span className="text-xs text-muted-foreground">读取超时（毫秒）</span>
                    <Input
                      type="number"
                      min={100}
                      disabled={!canUpdate}
                      {...form.register("readTimeoutMs")}
                    />
                  </label>
                  <div className="flex items-center justify-between gap-4 rounded-lg border p-4 sm:col-span-2">
                    <div className="space-y-1">
                      <p className="text-sm font-medium">免费供应商</p>
                      <p className="text-xs text-muted-foreground">参与免费优先调度。</p>
                    </div>
                    <Switch
                      checked={free}
                      disabled={!canUpdate}
                      onCheckedChange={(checked) =>
                        form.setValue("free", checked, { shouldDirty: true })
                      }
                      aria-label="免费供应商"
                    />
                  </div>
                </div>
              ) : null}
            </section>
          </div>
        </AutoScrollArea>

        <DialogFooter className="sm:justify-between">
          <div>
            {canDelete ? (
              <Button
                type="button"
                variant="ghost"
                className="text-destructive hover:text-destructive"
                onClick={() => setDeleteProviderOpen(true)}
              >
                <Trash2 />
                移除
              </Button>
            ) : null}
          </div>
          <div className="flex flex-col-reverse gap-2 sm:flex-row">
            <Button type="button" variant="outline" onClick={onClose}>
              关闭
            </Button>
            {canUpdate ? (
              <Button
                type="button"
                disabled={saveMutation.isPending}
                onClick={() => void onSubmit()}
              >
                {saveMutation.isPending ? <Loader2 className="animate-spin" /> : null}
                保存
              </Button>
            ) : null}
          </div>
        </DialogFooter>
      </DialogContent>

      {accountTarget !== undefined ? (
        <AccountDialog
          provider={provider}
          account={accountTarget ?? undefined}
          onClose={() => setAccountTarget(undefined)}
        />
      ) : null}
      {quotaTarget ? (
        <QuotaDialog account={quotaTarget} onClose={() => setQuotaTarget(null)} />
      ) : null}
      {modelTarget ? (
        <ModelDialog provider={provider} model={modelTarget} onClose={() => setModelTarget(null)} />
      ) : null}

      <ConfirmDialog
        open={deleteProviderOpen}
        onOpenChange={setDeleteProviderOpen}
        title={`删除供应商 ${provider.spec.displayName || name}`}
        desc="删除供应商不会自动删除其账号和模型，请确认这些资源配置不再使用。"
        confirmText="删除供应商"
        destructive
        isLoading={deleteProviderMutation.isPending}
        handleConfirm={() => deleteProviderMutation.mutate()}
      />
      <ConfirmDialog
        open={deleteAccountTarget !== null}
        onOpenChange={(open) => {
          if (!open) {
            setDeleteAccountTarget(null);
          }
        }}
        title={`删除密钥 ${deleteAccountTarget?.spec.displayName || deleteAccountTarget?.metadata.name || ""}`}
        desc="删除后该密钥将不再参与供应商调度。"
        confirmText="删除密钥"
        destructive
        isLoading={deleteAccountMutation.isPending}
        handleConfirm={() => {
          if (deleteAccountTarget) {
            deleteAccountMutation.mutate(deleteAccountTarget);
          }
        }}
      />
      <ConfirmDialog
        open={deleteModelTarget !== null}
        onOpenChange={(open) => {
          if (!open) {
            setDeleteModelTarget(null);
          }
        }}
        title={`删除模型 ${deleteModelTarget?.spec.modelCode || ""}`}
        desc="删除模型后，引用该模型的路由组可能无法正常工作。"
        confirmText="删除模型"
        destructive
        isLoading={deleteModelMutation.isPending}
        handleConfirm={() => {
          if (deleteModelTarget) {
            deleteModelMutation.mutate(deleteModelTarget);
          }
        }}
      />
    </Dialog>
  );
}

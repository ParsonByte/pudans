import { useMemo, useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CircleAlert, Loader2, Pencil, Plus, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  ConsoleEmpty,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";

import {
  AI_RUNTIME_PROVIDERS_QUERY_KEY,
  AI_SANDBOX_PROVIDERS_QUERY_KEY,
  type SandboxProviderItem,
  deleteSandboxProvider,
  fetchRuntimeProviders,
  fetchSandboxProviders,
  patchSandboxProvider,
  resolveAgentRuntimeError,
} from "../lib/api";
import { runtimeProviderLabel } from "../lib/model";
import { RuntimeLoadError, RuntimeTableSkeleton } from "./runtime-states";
import { SandboxProviderDialog } from "./sandbox-provider-dialog";

type SandboxProviderSettingsProps = {
  canCreate: boolean;
  canUpdate: boolean;
  canDelete: boolean;
};

export function SandboxProviderSettings({
  canCreate,
  canUpdate,
  canDelete,
}: SandboxProviderSettingsProps) {
  const queryClient = useQueryClient();
  const [dialogTarget, setDialogTarget] = useState<SandboxProviderItem | null | undefined>(
    undefined,
  );
  const [deleteTarget, setDeleteTarget] = useState<SandboxProviderItem | null>(null);

  const providersQuery = useQuery({
    queryKey: AI_SANDBOX_PROVIDERS_QUERY_KEY,
    queryFn: fetchSandboxProviders,
    retry: false,
  });
  const activeQuery = useQuery({
    queryKey: AI_RUNTIME_PROVIDERS_QUERY_KEY,
    queryFn: fetchRuntimeProviders,
    retry: false,
  });

  const providers = providersQuery.data ?? [];
  const activeByProvider = useMemo(
    () => new Map((activeQuery.data ?? []).map((info) => [info.provider, info])),
    [activeQuery.data],
  );

  const invalidateProviders = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: AI_SANDBOX_PROVIDERS_QUERY_KEY }),
      queryClient.invalidateQueries({ queryKey: AI_RUNTIME_PROVIDERS_QUERY_KEY }),
    ]);
  };

  const toggleMutation = useMutation({
    mutationFn: (item: SandboxProviderItem) =>
      patchSandboxProvider(item.metadata.name, [
        { op: "add", path: "/spec/enabled", value: item.spec.enabled === false },
      ]),
    onSuccess: async () => {
      toast.success("沙箱提供方状态已更新。");
      await invalidateProviders();
    },
    onError: (error) => toast.error(resolveAgentRuntimeError(error)),
  });

  const deleteMutation = useMutation({
    mutationFn: (item: SandboxProviderItem) => deleteSandboxProvider(item.metadata.name),
    onSuccess: async () => {
      setDeleteTarget(null);
      toast.success("沙箱提供方已删除。");
      await invalidateProviders();
    },
    onError: (error) => toast.error(resolveAgentRuntimeError(error)),
  });

  return (
    <div className="space-y-4">
      <Alert>
        <CircleAlert />
        <AlertTitle>配置缺失会导致启动失败</AlertTitle>
        <AlertDescription>
          启动 Agent 沙箱需要对应提供方的启用配置与有效密钥；未配置或已停用时，启动请求会返回
          ai-runtime-provider-missing。
        </AlertDescription>
      </Alert>

      <ConsoleSection className="overflow-hidden">
        <ConsoleSectionHeader
          title="沙箱提供方"
          description="管理阿里云与腾讯云沙箱凭据、接入地址与默认镜像。AccessKey Secret 只写，不会返回明文。"
          action={
            canCreate ? (
              <Button type="button" variant="outline" onClick={() => setDialogTarget(null)}>
                <Plus />
                新增提供方
              </Button>
            ) : undefined
          }
        />

        {providersQuery.isPending ? (
          <RuntimeTableSkeleton />
        ) : providersQuery.isError ? (
          <RuntimeLoadError
            title="无法加载沙箱提供方"
            description={resolveAgentRuntimeError(providersQuery.error)}
            onRetry={() => void providersQuery.refetch()}
          />
        ) : providers.length === 0 ? (
          <ConsoleEmpty>
            还没有沙箱提供方配置。新增阿里云或腾讯云提供方后，Agent 才能启动沙箱。
          </ConsoleEmpty>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="px-4">提供方</TableHead>
                <TableHead className="px-4">名称与凭据</TableHead>
                <TableHead className="px-4">区域与接入地址</TableHead>
                <TableHead className="px-4">默认镜像</TableHead>
                <TableHead className="px-4 text-right">最大超时</TableHead>
                <TableHead className="px-4">状态</TableHead>
                <TableHead className="px-4 text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {providers.map((item) => {
                const enabled = item.spec.enabled !== false;
                const active = activeByProvider.get(item.spec.provider);
                const toggling =
                  toggleMutation.isPending &&
                  toggleMutation.variables?.metadata.name === item.metadata.name;
                return (
                  <TableRow key={item.metadata.name}>
                    <TableCell className="px-4">
                      <Badge variant="outline">{runtimeProviderLabel(item.spec.provider)}</Badge>
                    </TableCell>
                    <TableCell className="px-4">
                      <p className="text-sm font-medium">
                        {item.spec.displayName || item.metadata.name}
                      </p>
                      <p className="text-[11px] text-muted-foreground">
                        {item.spec.accessKeyId ? "凭据已配置" : "凭据未配置"}
                      </p>
                    </TableCell>
                    <TableCell className="max-w-56 px-4">
                      <p className="text-sm">{item.spec.region || "—"}</p>
                      <p className="truncate font-mono text-[11px] text-muted-foreground">
                        {item.spec.endpoint || "使用区域默认地址"}
                      </p>
                    </TableCell>
                    <TableCell className="max-w-48 px-4">
                      <p className="truncate font-mono text-xs" title={item.spec.defaultImage}>
                        {item.spec.defaultImage || "—"}
                      </p>
                    </TableCell>
                    <TableCell className="px-4 text-right text-sm">
                      {item.spec.maxTimeoutSeconds ?? 3600} 秒
                    </TableCell>
                    <TableCell className="px-4">
                      <div className="flex flex-wrap items-center gap-1.5">
                        {enabled ? (
                          <Badge variant={active ? "secondary" : "outline"}>
                            {activeQuery.isPending
                              ? "检查中"
                              : active
                                ? "可用"
                                : activeQuery.isError
                                  ? "未知"
                                  : "未生效"}
                          </Badge>
                        ) : (
                          <Badge variant="outline">已停用</Badge>
                        )}
                        {toggling ? (
                          <Loader2 className="size-4 animate-spin text-muted-foreground" />
                        ) : null}
                        <Switch
                          checked={enabled}
                          disabled={!canUpdate || toggling}
                          onCheckedChange={() => toggleMutation.mutate(item)}
                          aria-label={enabled ? "停用沙箱提供方" : "启用沙箱提供方"}
                        />
                      </div>
                    </TableCell>
                    <TableCell className="px-4 text-right">
                      <div className="flex items-center justify-end gap-1">
                        {canUpdate ? (
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon-sm"
                            aria-label="编辑沙箱提供方"
                            title="编辑沙箱提供方"
                            onClick={() => setDialogTarget(item)}
                          >
                            <Pencil />
                          </Button>
                        ) : null}
                        {canDelete ? (
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon-sm"
                            className="text-destructive hover:text-destructive"
                            aria-label="删除沙箱提供方"
                            title="删除沙箱提供方"
                            onClick={() => setDeleteTarget(item)}
                          >
                            <Trash2 />
                          </Button>
                        ) : null}
                        {!canUpdate && !canDelete ? (
                          <span className="text-xs text-muted-foreground">—</span>
                        ) : null}
                      </div>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        )}

        {activeQuery.isError ? (
          <p className="border-t px-4 py-2 text-xs text-destructive">
            无法读取可用提供方状态：{resolveAgentRuntimeError(activeQuery.error)}
          </p>
        ) : null}
      </ConsoleSection>

      {dialogTarget !== undefined ? (
        <SandboxProviderDialog
          provider={dialogTarget ?? undefined}
          onClose={() => setDialogTarget(undefined)}
        />
      ) : null}

      {deleteTarget ? (
        <ConfirmDialog
          open
          onOpenChange={(open) => !open && setDeleteTarget(null)}
          title="删除沙箱提供方"
          desc={
            <span>
              将删除提供方「{deleteTarget.spec.displayName || deleteTarget.metadata.name}
              」。使用该提供方的 Agent 将无法启动新沙箱，正在运行的沙箱不会被销毁。
            </span>
          }
          confirmText="删除"
          destructive
          isLoading={deleteMutation.isPending}
          handleConfirm={() => deleteMutation.mutate(deleteTarget)}
        />
      ) : null}
    </div>
  );
}

"use client";

import { useMemo, useState } from "react";

import type { ListedAuthProvider } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, Lock, Pencil, Plus, RefreshCw, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";
import { DataTablePagination, DataTableToolbar, LongText } from "@/components/data-table";
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
import { ConsoleSection } from "@/modules/workspace/console/components/console-page";
import {
  ConsoleState,
  ConsoleTableSkeleton,
} from "@/modules/workspace/console/components/console-state";
import {
  type ExtensionRecord,
  isImmutableExtension,
} from "@/modules/workspace/console/settings/lib/extension-records";

import {
  deleteAuthProviderRecord,
  listAuthProviderRecords,
  listAuthProviderStates,
  resolveAuthProviderError,
  setAuthProviderEnabled,
} from "../lib/auth-provider-api";
import { AuthProviderDialog } from "./auth-provider-dialog";

type DialogState = { mode: "create" } | { mode: "edit"; record: ExtensionRecord };

export function AuthProviderSection() {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [dialog, setDialog] = useState<DialogState | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<ExtensionRecord | null>(null);

  const recordsQuery = useQuery({
    queryKey: ["auth-provider-resources"],
    queryFn: listAuthProviderRecords,
    retry: false,
  });
  const statesQuery = useQuery({
    queryKey: ["auth-provider-states"],
    queryFn: listAuthProviderStates,
    retry: false,
  });

  const stateByName = useMemo(() => {
    const map = new Map<string, ListedAuthProvider>();
    for (const state of statesQuery.data ?? []) {
      map.set(state.name, state);
    }
    return map;
  }, [statesQuery.data]);

  const normalizedSearch = search.trim().toLowerCase();
  const filteredRecords = useMemo(
    () =>
      (recordsQuery.data ?? []).filter((record) => {
        if (!normalizedSearch) {
          return true;
        }
        return [
          record.metadata.name,
          stringSpec(record, "displayName"),
          stringSpec(record, "description"),
          stringSpec(record, "authenticationUrl"),
        ]
          .filter(Boolean)
          .join("\n")
          .toLowerCase()
          .includes(normalizedSearch);
      }),
    [normalizedSearch, recordsQuery.data],
  );

  const pageCount = Math.max(1, Math.ceil(filteredRecords.length / pageSize));
  const currentPage = Math.min(page, pageCount);
  const pageItems = filteredRecords.slice((currentPage - 1) * pageSize, currentPage * pageSize);

  const toggleMutation = useMutation({
    mutationFn: ({ name, enabled }: { name: string; enabled: boolean }) =>
      setAuthProviderEnabled(name, enabled),
    onSuccess: async (_, variables) => {
      await queryClient.invalidateQueries({ queryKey: ["auth-provider-states"] });
      toast.success(variables.enabled ? "登录提供方已启用。" : "登录提供方已停用。");
    },
    onError: (error) => {
      toast.error(resolveAuthProviderError(error));
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (record: ExtensionRecord) => deleteAuthProviderRecord(record.metadata.name),
    onSuccess: async (_, record) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["auth-provider-resources"] }),
        queryClient.invalidateQueries({ queryKey: ["auth-provider-states"] }),
      ]);
      setDeleteTarget(null);
      toast.success(`登录提供方「${record.metadata.name}」已删除。`);
    },
    onError: (error) => {
      toast.error(resolveAuthProviderError(error));
    },
  });

  const refreshing = recordsQuery.isFetching || statesQuery.isFetching;
  const refresh = () => {
    void Promise.all([recordsQuery.refetch(), statesQuery.refetch()]);
  };

  return (
    <div className="space-y-4">
      <div className="space-y-1">
        <h2 className="text-sm font-semibold">登录提供方</h2>
        <p className="text-xs leading-5 text-muted-foreground">
          登录提供方来自 authproviders 扩展资源，启用状态由 auth-providers
          接口维护；系统内置提供方不可修改。
        </p>
      </div>

      <DataTableToolbar
        searchPlaceholder="搜索提供方名称或地址..."
        searchValue={search}
        onSearchChange={(value) => {
          setSearch(value);
          setPage(1);
        }}
        isFiltered={search.trim().length > 0}
        onReset={() => {
          setSearch("");
          setPage(1);
        }}
        viewOptions={
          <div className="flex items-center gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={refresh}
              disabled={refreshing}
            >
              {refreshing ? <Loader2 className="animate-spin" /> : <RefreshCw />}
              刷新
            </Button>
            <Button type="button" size="sm" onClick={() => setDialog({ mode: "create" })}>
              <Plus />
              新建提供方
            </Button>
          </div>
        }
      />

      <ConsoleSection>
        {recordsQuery.isPending ? (
          <Table>
            <TableBody>
              <ConsoleTableSkeleton columns={5} rows={4} cellClassName="px-5 py-3" />
            </TableBody>
          </Table>
        ) : recordsQuery.isError ? (
          <ConsoleState
            variant="error"
            title="无法加载登录提供方"
            description={resolveAuthProviderError(recordsQuery.error)}
            onRetry={() => void recordsQuery.refetch()}
          />
        ) : pageItems.length === 0 ? (
          <ConsoleState
            variant="empty"
            title={search.trim() ? "没有匹配的登录提供方。" : "暂无登录提供方。"}
          />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="px-5">提供方</TableHead>
                <TableHead className="px-5">类型</TableHead>
                <TableHead className="px-5">登录地址</TableHead>
                <TableHead className="px-5">状态</TableHead>
                <TableHead className="px-5 text-end">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {pageItems.map((record) => {
                const state = stateByName.get(record.metadata.name);
                const immutable = isImmutableExtension(record) || state?.privileged === true;
                const enabled = state?.enabled === true;
                const authType = stringSpec(record, "authType");

                return (
                  <TableRow key={record.metadata.name}>
                    <TableCell className="px-5 py-3">
                      <div className="min-w-0 space-y-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <LongText className="max-w-64 text-sm font-medium">
                            {stringSpec(record, "displayName") || record.metadata.name}
                          </LongText>
                          {immutable ? (
                            <Badge variant="secondary">
                              <Lock className="size-3" />
                              内置
                            </Badge>
                          ) : null}
                        </div>
                        <LongText className="max-w-64 font-mono text-xs text-muted-foreground">
                          {record.metadata.name}
                        </LongText>
                        {stringSpec(record, "description") ? (
                          <LongText className="max-w-80 text-xs text-muted-foreground">
                            {stringSpec(record, "description")}
                          </LongText>
                        ) : null}
                      </div>
                    </TableCell>
                    <TableCell className="px-5 py-3">
                      <Badge variant="outline">{authTypeLabel(authType)}</Badge>
                    </TableCell>
                    <TableCell className="px-5 py-3">
                      <LongText className="max-w-64 font-mono text-xs text-muted-foreground">
                        {stringSpec(record, "authenticationUrl") || "—"}
                      </LongText>
                    </TableCell>
                    <TableCell className="px-5 py-3">
                      {statesQuery.isPending ? (
                        <span className="text-xs text-muted-foreground">状态加载中</span>
                      ) : immutable ? (
                        <div className="flex items-center gap-2">
                          <Badge variant={enabled ? "secondary" : "outline"}>
                            {enabled ? "已启用" : "已停用"}
                          </Badge>
                          <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
                            <Lock className="size-3.5" />
                            系统内置
                          </span>
                        </div>
                      ) : state ? (
                        <div className="flex items-center gap-2">
                          <Switch
                            checked={enabled}
                            disabled={toggleMutation.isPending}
                            aria-label={`${enabled ? "停用" : "启用"}${record.metadata.name}`}
                            onCheckedChange={(checked) =>
                              toggleMutation.mutate({
                                name: record.metadata.name,
                                enabled: checked,
                              })
                            }
                          />
                          <span className="text-xs text-muted-foreground">
                            {enabled ? "已启用" : "已停用"}
                          </span>
                        </div>
                      ) : (
                        <span className="text-xs text-muted-foreground">状态未知</span>
                      )}
                    </TableCell>
                    <TableCell className="px-5 py-3 text-end">
                      {immutable ? (
                        <span className="inline-flex items-center gap-1.5 text-xs text-muted-foreground">
                          <Lock className="size-3.5" />
                          内置资源不可修改
                        </span>
                      ) : (
                        <div className="flex justify-end gap-2">
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => setDialog({ mode: "edit", record })}
                          >
                            <Pencil />
                            编辑
                          </Button>
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => setDeleteTarget(record)}
                          >
                            <Trash2 />
                            删除
                          </Button>
                        </div>
                      )}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        )}
      </ConsoleSection>

      <DataTablePagination
        currentPage={currentPage}
        pageCount={pageCount}
        pageSize={pageSize}
        onPageChange={setPage}
        onPageSizeChange={(value) => {
          setPageSize(value);
          setPage(1);
        }}
        disabled={recordsQuery.isFetching}
      />

      {dialog ? (
        <AuthProviderDialog
          record={dialog.mode === "edit" ? dialog.record : undefined}
          onClose={() => setDialog(null)}
        />
      ) : null}

      <ConfirmDialog
        open={Boolean(deleteTarget)}
        onOpenChange={(open) => {
          if (!open) {
            setDeleteTarget(null);
          }
        }}
        title="删除登录提供方"
        desc={
          deleteTarget
            ? `确定删除 AuthProvider「${deleteTarget.metadata.name}」？删除后登录页将不再展示该提供方。`
            : ""
        }
        confirmText="删除"
        destructive
        isLoading={deleteMutation.isPending}
        handleConfirm={() => {
          if (deleteTarget) {
            deleteMutation.mutate(deleteTarget);
          }
        }}
      />
    </div>
  );
}

function stringSpec(record: ExtensionRecord, key: string): string {
  const value = record.spec?.[key];
  return typeof value === "string" ? value : "";
}

function authTypeLabel(authType: string): string {
  if (authType === "FORM") {
    return "表单";
  }
  if (authType === "OAUTH2") {
    return "OAuth2";
  }
  return authType || "未配置";
}

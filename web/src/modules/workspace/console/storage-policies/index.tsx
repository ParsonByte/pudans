import { useMemo, useState } from "react";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, Pencil, Plus, RefreshCw, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";
import { DataTablePagination } from "@/components/data-table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  ConsoleEmpty,
  ConsolePage,
  ConsoleSection,
} from "@/modules/workspace/console/components/console-page";
import { useAllowedOperations } from "@/modules/workspace/lib/permissions";

import { PolicyDialog } from "./components/policy-dialog";
import { StorageTypeIcon } from "./components/type-icon";
import { TypePickerDialog } from "./components/type-picker-dialog";
import {
  type StoragePolicyItem,
  type StoragePolicyType,
  type StoragePolicyTypeDescriptor,
  deleteStoragePolicy,
  fetchStoragePolicies,
  fetchStoragePolicyTypes,
  resolveStorageApiError,
} from "./lib/api";
import {
  createStoragePolicyDraft,
  formatFileSize,
  isLocal,
  orderedStorageTypes,
  policyEndpoint,
  storageTypeDescription,
  storageTypeLabel,
} from "./lib/model";

const EMPTY_TYPES: StoragePolicyTypeDescriptor[] = [];

export function StoragePoliciesView() {
  const queryClient = useQueryClient();
  const allowedOperations = useAllowedOperations();
  const canCreate = allowedOperations.has("create");
  const canUpdate = allowedOperations.has("update");
  const canDelete = allowedOperations.has("delete");
  const canTest = allowedOperations.has("test");
  const canOAuth = allowedOperations.has("oauth");
  const [createOpen, setCreateOpen] = useState(false);
  const [createType, setCreateType] = useState<StoragePolicyType | null>(null);
  const [editing, setEditing] = useState<StoragePolicyItem | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<StoragePolicyItem | null>(null);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);

  const policiesQuery = useQuery({
    queryKey: ["storage-policies", page, size],
    queryFn: () => fetchStoragePolicies(page, size),
    placeholderData: keepPreviousData,
    retry: false,
  });
  const typesQuery = useQuery({
    queryKey: ["storage-policy-types"],
    queryFn: fetchStoragePolicyTypes,
    retry: false,
  });

  const types = useMemo(
    () => orderedStorageTypes(typesQuery.data ?? EMPTY_TYPES),
    [typesQuery.data],
  );
  const descriptors = useMemo(() => new Map(types.map((type) => [type.type, type])), [types]);
  const policies = policiesQuery.data?.items ?? [];
  const total = policiesQuery.data?.total ?? 0;
  const pageCount = Math.max(1, Math.ceil(total / size));

  const deleteMutation = useMutation({
    mutationFn: (policy: StoragePolicyItem) => deleteStoragePolicy(policy.metadata.name),
    onSuccess: async () => {
      if (policies.length === 1 && page > 1) {
        setPage(page - 1);
      }
      await queryClient.invalidateQueries({ queryKey: ["storage-policies"] });
      setDeleteTarget(null);
      toast.success("存储策略已删除。");
    },
    onError: (error) => {
      setDeleteTarget(null);
      toast.error(resolveStorageApiError(error));
    },
  });

  const refreshing = policiesQuery.isFetching || typesQuery.isFetching;
  const refresh = () => {
    void policiesQuery.refetch();
    void typesQuery.refetch();
  };

  return (
    <ConsolePage title="存储">
      <div className="flex flex-1 flex-col gap-4 sm:gap-6">
        <div className="flex flex-wrap items-center justify-end gap-2">
          <Button type="button" variant="outline" onClick={refresh} disabled={refreshing}>
            {refreshing ? <Loader2 className="animate-spin" /> : <RefreshCw />}
            刷新
          </Button>
          {canCreate ? (
            <Button type="button" onClick={() => setCreateOpen(true)}>
              <Plus />
              添加存储策略
            </Button>
          ) : null}
        </div>

        {policiesQuery.isPending ? (
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {Array.from({ length: 3 }, (_, index) => (
              <Skeleton key={index} className="h-56 rounded-xl" />
            ))}
          </div>
        ) : policiesQuery.isError ? (
          <ConsoleSection className="flex min-h-56 flex-col items-center justify-center gap-3 p-8 text-center">
            <p className="text-sm font-medium">无法加载存储策略</p>
            <p className="text-xs text-muted-foreground">
              {resolveStorageApiError(policiesQuery.error)}
            </p>
            <Button type="button" variant="outline" size="sm" onClick={() => void refresh()}>
              重试
            </Button>
          </ConsoleSection>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {canCreate && page === 1 ? (
              <button
                type="button"
                onClick={() => setCreateOpen(true)}
                className="flex min-h-56 flex-col items-center justify-center gap-3 rounded-xl border border-dashed p-6 text-muted-foreground transition-colors hover:border-primary/50 hover:bg-accent/30 focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
              >
                <span className="flex size-12 items-center justify-center rounded-full bg-muted">
                  <Plus className="size-5" />
                </span>
                <span className="text-sm font-medium">添加存储策略</span>
                <span className="text-xs">本机、对象存储或 OneDrive</span>
              </button>
            ) : null}

            {policies.map((policy) => (
              <PolicyCard
                key={policy.metadata.name}
                policy={policy}
                descriptor={descriptors.get(policy.spec.type)}
                canUpdate={canUpdate}
                canDelete={canDelete}
                onEdit={() => setEditing(policy)}
                onDelete={() => setDeleteTarget(policy)}
              />
            ))}

            {policies.length === 0 && !canCreate ? (
              <ConsoleSection className="sm:col-span-2 xl:col-span-3">
                <ConsoleEmpty>
                  {total === 0 ? "暂无可用的存储策略。" : "当前页没有存储策略。"}
                </ConsoleEmpty>
              </ConsoleSection>
            ) : null}
          </div>
        )}

        {!policiesQuery.isPending && !policiesQuery.isError && total > 0 ? (
          <DataTablePagination
            currentPage={page}
            pageCount={pageCount}
            pageSize={size}
            onPageChange={setPage}
            onPageSizeChange={(value) => {
              setSize(value);
              setPage(1);
            }}
            disabled={policiesQuery.isFetching}
            className="mt-auto"
          />
        ) : null}
      </div>

      {createOpen ? (
        <TypePickerDialog
          types={types}
          onSelect={(type) => {
            setCreateOpen(false);
            setCreateType(type);
          }}
          onClose={() => setCreateOpen(false)}
        />
      ) : null}

      {createType ? (
        <PolicyDialog
          mode="create"
          policy={createStoragePolicyDraft(createType)}
          descriptor={descriptors.get(createType)}
          canTest={false}
          canOAuth={false}
          onClose={() => setCreateType(null)}
        />
      ) : null}

      {editing ? (
        <PolicyDialog
          mode="edit"
          policy={editing}
          descriptor={descriptors.get(editing.spec.type)}
          canTest={canTest}
          canOAuth={canOAuth}
          onClose={() => setEditing(null)}
        />
      ) : null}

      <ConfirmDialog
        open={Boolean(deleteTarget)}
        onOpenChange={(open) => {
          if (!open) {
            setDeleteTarget(null);
          }
        }}
        title="删除存储策略"
        desc={
          deleteTarget
            ? `确定删除「${deleteTarget.spec.displayName}」吗？仍被文件引用或作为默认策略时，删除会被拒绝。`
            : ""
        }
        destructive
        isLoading={deleteMutation.isPending}
        confirmText={deleteMutation.isPending ? "删除中..." : "删除"}
        handleConfirm={() => {
          if (deleteTarget) {
            deleteMutation.mutate(deleteTarget);
          }
        }}
      />
    </ConsolePage>
  );
}

function PolicyCard({
  policy,
  descriptor,
  canUpdate,
  canDelete,
  onEdit,
  onDelete,
}: {
  policy: StoragePolicyItem;
  descriptor?: StoragePolicyTypeDescriptor;
  canUpdate: boolean;
  canDelete: boolean;
  onEdit: () => void;
  onDelete: () => void;
}) {
  const type = policy.spec.type;
  const ready = policy.status?.ready !== false;
  const endpoint = policyEndpoint(policy);
  const bucket = isLocal(type) ? "本机存储" : policy.spec.bucketName || "—";
  const descriptorList = descriptor ? [descriptor] : [];
  const description = policy.spec.description || storageTypeDescription(type, descriptorList);

  return (
    <ConsoleSection className="flex flex-col gap-4 p-5">
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 items-center gap-3">
          <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-muted text-muted-foreground">
            <StorageTypeIcon type={type} className="size-5" />
          </span>
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h3 className="truncate text-sm font-semibold">{policy.spec.displayName}</h3>
              {policy.spec.defaultPolicy ? <Badge>默认</Badge> : null}
            </div>
            <p className="text-xs text-muted-foreground">
              {storageTypeLabel(type, descriptorList)}
            </p>
          </div>
        </div>
        <Badge variant={ready ? "secondary" : "outline"}>{ready ? "就绪" : "待就绪"}</Badge>
      </div>

      <p className="line-clamp-2 min-h-10 text-sm text-muted-foreground">{description}</p>

      <dl className="grid gap-2 text-xs">
        <div className="flex items-center justify-between gap-4">
          <dt className="text-muted-foreground">Bucket</dt>
          <dd className="truncate font-mono">{bucket}</dd>
        </div>
        <div className="flex items-center justify-between gap-4">
          <dt className="text-muted-foreground">EndPoint</dt>
          <dd className="truncate font-mono">{endpoint ?? "—"}</dd>
        </div>
        <div className="flex items-center justify-between gap-4">
          <dt className="text-muted-foreground">文件上限</dt>
          <dd>{formatFileSize(policy.spec.maxSize)}</dd>
        </div>
      </dl>

      {!ready && policy.status?.message ? (
        <p className="text-xs text-destructive">{policy.status.message}</p>
      ) : null}

      <div className="mt-auto flex items-center justify-between gap-3 border-t pt-3">
        <span className="min-w-0 truncate font-mono text-[11px] text-muted-foreground">
          {policy.metadata.name}
        </span>
        <div className="flex items-center gap-2">
          {canUpdate ? (
            <Button type="button" variant="outline" size="sm" onClick={onEdit}>
              <Pencil />
              编辑
            </Button>
          ) : null}
          {canDelete ? (
            <Button
              type="button"
              variant="ghost"
              size="icon"
              aria-label={`删除 ${policy.spec.displayName}`}
              onClick={onDelete}
            >
              <Trash2 />
            </Button>
          ) : null}
        </div>
      </div>
    </ConsoleSection>
  );
}

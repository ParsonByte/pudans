import { useEffect, useMemo, useState } from "react";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Download, Flame, Loader2, Plus, RefreshCw, RotateCcw, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";
import {
  DataTableFacetedFilter,
  DataTablePagination,
  DataTableToolbar,
  LongText,
} from "@/components/data-table";
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
import { formatBeijingDateTime } from "@/lib/date";
import { ConsoleEmpty, ConsolePage } from "@/modules/workspace/console/components/console-page";
import {
  ConsoleState,
  ConsoleTableSkeleton,
} from "@/modules/workspace/console/components/console-state";
import {
  type StoragePolicyItem,
  fetchStoragePolicies,
} from "@/modules/workspace/console/storage-policies/lib/api";
import { useAllowedOperations } from "@/modules/workspace/lib/permissions";

import { StorageUploadDialog } from "./components/upload-dialog";
import {
  type StorageFileItem,
  type StorageFileState,
  createStorageFileDownloadUrl,
  fetchStorageFiles,
  purgeStorageFile,
  resolveStorageFileApiError,
  restoreStorageFile,
  trashStorageFile,
} from "./lib/api";
import {
  STORAGE_FILE_STATE_OPTIONS,
  formatStorageFileSize,
  storageFileName,
  storageFileStateLabel,
} from "./lib/model";

const EMPTY_POLICIES: StoragePolicyItem[] = [];

type ConfirmAction = "trash" | "restore" | "purge";

type ConfirmTarget = {
  action: ConfirmAction;
  file: StorageFileItem;
};

const CONFIRM_COPY: Record<
  ConfirmAction,
  { title: string; confirmText: string; destructive: boolean; success: string }
> = {
  trash: {
    title: "移入回收站",
    confirmText: "移入回收站",
    destructive: false,
    success: "文件已移入回收站。",
  },
  restore: {
    title: "恢复文件",
    confirmText: "恢复",
    destructive: false,
    success: "文件已恢复。",
  },
  purge: {
    title: "彻底删除文件",
    confirmText: "彻底删除",
    destructive: true,
    success: "文件已彻底删除。",
  },
};

export function StorageFilesView() {
  const queryClient = useQueryClient();
  const allowedOperations = useAllowedOperations();
  const canUpload = allowedOperations.has("create");
  const canTrash = allowedOperations.has("delete");
  const canRestore = allowedOperations.has("restore");
  const canPurge = allowedOperations.has("purge");
  const canDownload = allowedOperations.has("download");
  const [searchInput, setSearchInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [stateFilters, setStateFilters] = useState<StorageFileState[]>([]);
  const [policyFilters, setPolicyFilters] = useState<string[]>([]);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [confirmTarget, setConfirmTarget] = useState<ConfirmTarget | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setKeyword(searchInput.trim());
      setPage(1);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [searchInput]);

  const filesQuery = useQuery({
    queryKey: ["storage-files", page, size, keyword, stateFilters, policyFilters],
    queryFn: () =>
      fetchStorageFiles({
        page,
        size,
        name: keyword || undefined,
        states: stateFilters,
        policies: policyFilters,
      }),
    placeholderData: keepPreviousData,
    retry: false,
  });
  const policiesQuery = useQuery({
    queryKey: ["storage-policies", "options"],
    queryFn: () => fetchStoragePolicies(1, 100),
    retry: false,
  });

  const policies = policiesQuery.data?.items ?? EMPTY_POLICIES;
  const policyOptions = useMemo(
    () =>
      policies.map((policy) => ({
        value: policy.metadata.name,
        label: policy.spec.displayName || policy.metadata.name,
      })),
    [policies],
  );
  const files = filesQuery.data?.items ?? [];
  const total = filesQuery.data?.total ?? 0;
  const pageCount = Math.max(1, Math.ceil(total / size));
  const isFiltered =
    searchInput.trim().length > 0 || stateFilters.length > 0 || policyFilters.length > 0;
  const emptyMessage = isFiltered ? "当前筛选没有匹配的存储文件。" : "暂无存储文件。";

  const confirmMutation = useMutation({
    mutationFn: async (target: ConfirmTarget) => {
      if (target.action === "trash") {
        return trashStorageFile(target.file.metadata.name);
      }
      if (target.action === "restore") {
        return restoreStorageFile(target.file.metadata.name);
      }
      return purgeStorageFile(target.file.metadata.name);
    },
    onSuccess: async (_result, target) => {
      await queryClient.invalidateQueries({ queryKey: ["storage-files"] });
      if (files.length === 1 && page > 1) {
        setPage(page - 1);
      }
      setConfirmTarget(null);
      toast.success(CONFIRM_COPY[target.action].success);
    },
    onError: (error) => {
      setConfirmTarget(null);
      toast.error(resolveStorageFileApiError(error));
    },
  });

  const downloadMutation = useMutation({
    mutationFn: (file: StorageFileItem) => createStorageFileDownloadUrl(file.metadata.name),
    onSuccess: (result) => {
      const link = document.createElement("a");
      link.href = result.url;
      link.target = "_blank";
      link.rel = "noopener";
      document.body.appendChild(link);
      link.click();
      link.remove();
    },
    onError: (error) => toast.error(resolveStorageFileApiError(error)),
  });

  const refreshing = filesQuery.isFetching || policiesQuery.isFetching;
  const refresh = () => {
    void filesQuery.refetch();
    void policiesQuery.refetch();
  };

  const confirmCopy = confirmTarget ? CONFIRM_COPY[confirmTarget.action] : CONFIRM_COPY.trash;
  const confirmDescription = confirmTarget ? confirmDescriptionFor(confirmTarget) : "";

  return (
    <ConsolePage
      title="存储文件"
      description="浏览各存储策略下的文件，管理回收站与下载。"
      actions={
        <div className="flex flex-wrap items-center gap-2">
          <Button type="button" variant="outline" onClick={refresh} disabled={refreshing}>
            {refreshing ? <Loader2 className="animate-spin" /> : <RefreshCw />}
            刷新
          </Button>
          {canUpload ? (
            <Button type="button" onClick={() => setUploadOpen(true)}>
              <Plus />
              上传文件
            </Button>
          ) : null}
        </div>
      }
    >
      <div className="flex flex-1 flex-col gap-4 sm:gap-6">
        <DataTableToolbar
          searchPlaceholder="按文件 ID 精确搜索…"
          searchValue={searchInput}
          onSearchChange={setSearchInput}
          filters={
            <>
              <DataTableFacetedFilter
                title="状态"
                options={STORAGE_FILE_STATE_OPTIONS}
                selected={stateFilters}
                onSelectedChange={(values) => {
                  setStateFilters(values as StorageFileState[]);
                  setPage(1);
                }}
              />
              <DataTableFacetedFilter
                title="存储策略"
                options={policyOptions}
                selected={policyFilters}
                onSelectedChange={(values) => {
                  setPolicyFilters(values);
                  setPage(1);
                }}
              />
            </>
          }
          isFiltered={isFiltered}
          onReset={() => {
            setSearchInput("");
            setKeyword("");
            setStateFilters([]);
            setPolicyFilters([]);
            setPage(1);
          }}
        />

        {filesQuery.isError ? (
          <ConsoleState
            variant="error"
            title="无法加载存储文件"
            description={resolveStorageFileApiError(filesQuery.error)}
            onRetry={() => void filesQuery.refetch()}
            wrapper
          />
        ) : (
          <div className="overflow-hidden border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="px-4">文件</TableHead>
                  <TableHead className="px-4">大小</TableHead>
                  <TableHead className="px-4">内容类型</TableHead>
                  <TableHead className="px-4">存储策略</TableHead>
                  <TableHead className="px-4">所有者</TableHead>
                  <TableHead className="px-4">创建时间</TableHead>
                  <TableHead className="px-4 text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {filesQuery.isPending ? (
                  <ConsoleTableSkeleton columns={7} rows={5} />
                ) : files.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={7}>
                      <ConsoleEmpty>{emptyMessage}</ConsoleEmpty>
                    </TableCell>
                  </TableRow>
                ) : (
                  files.map((file) => {
                    const name = storageFileName(file);
                    const downloading =
                      downloadMutation.isPending &&
                      downloadMutation.variables?.metadata.name === file.metadata.name;
                    return (
                      <TableRow key={file.metadata.name}>
                        <TableCell className="px-4 py-3">
                          <LongText className="max-w-64 text-sm font-medium">{name}</LongText>
                          <div className="mt-1 flex flex-wrap items-center gap-2">
                            <span className="font-mono text-[11px] text-muted-foreground">
                              {file.metadata.name}
                            </span>
                            <Badge
                              variant={file.spec.state === "ACTIVE" ? "secondary" : "outline"}
                              className={
                                file.spec.state === "UPLOADING" ? "animate-pulse" : undefined
                              }
                            >
                              {storageFileStateLabel(file.spec.state)}
                            </Badge>
                          </div>
                        </TableCell>
                        <TableCell className="px-4 py-3 tabular-nums">
                          {formatStorageFileSize(file.spec.size)}
                        </TableCell>
                        <TableCell className="px-4 py-3">
                          <LongText className="max-w-40 text-xs text-muted-foreground">
                            {file.spec.contentType || "—"}
                          </LongText>
                        </TableCell>
                        <TableCell className="px-4 py-3">
                          <LongText className="max-w-40 font-mono text-xs">
                            {file.spec.policy || "—"}
                          </LongText>
                        </TableCell>
                        <TableCell className="px-4 py-3">
                          <LongText className="max-w-40 font-mono text-xs text-muted-foreground">
                            {file.spec.owner || "—"}
                          </LongText>
                        </TableCell>
                        <TableCell className="px-4 py-3 text-xs text-muted-foreground">
                          {formatBeijingDateTime(file.metadata.creationTimestamp)}
                        </TableCell>
                        <TableCell className="px-4 py-3">
                          <div className="flex items-center justify-end gap-1">
                            {file.spec.state === "ACTIVE" && canDownload ? (
                              <Button
                                type="button"
                                variant="ghost"
                                size="icon-sm"
                                aria-label={`下载 ${name}`}
                                disabled={downloading}
                                onClick={() => downloadMutation.mutate(file)}
                              >
                                {downloading ? <Loader2 className="animate-spin" /> : <Download />}
                              </Button>
                            ) : null}
                            {file.spec.state === "ACTIVE" && canTrash ? (
                              <Button
                                type="button"
                                variant="ghost"
                                size="icon-sm"
                                aria-label={`将 ${name} 移入回收站`}
                                onClick={() => setConfirmTarget({ action: "trash", file })}
                              >
                                <Trash2 />
                              </Button>
                            ) : null}
                            {file.spec.state === "TRASHED" && canRestore ? (
                              <Button
                                type="button"
                                variant="ghost"
                                size="icon-sm"
                                aria-label={`恢复 ${name}`}
                                onClick={() => setConfirmTarget({ action: "restore", file })}
                              >
                                <RotateCcw />
                              </Button>
                            ) : null}
                            {(file.spec.state === "TRASHED" || file.spec.state === "UPLOADING") &&
                            canPurge ? (
                              <Button
                                type="button"
                                variant="ghost"
                                size="icon-sm"
                                aria-label={`彻底删除 ${name}`}
                                onClick={() => setConfirmTarget({ action: "purge", file })}
                              >
                                <Flame />
                              </Button>
                            ) : null}
                          </div>
                        </TableCell>
                      </TableRow>
                    );
                  })
                )}
              </TableBody>
            </Table>
          </div>
        )}

        {!filesQuery.isError && total > 0 ? (
          <DataTablePagination
            currentPage={page}
            pageCount={pageCount}
            pageSize={size}
            onPageChange={setPage}
            onPageSizeChange={(value) => {
              setSize(value);
              setPage(1);
            }}
            disabled={filesQuery.isFetching}
            className="mt-auto"
          />
        ) : null}
      </div>

      {uploadOpen ? (
        <StorageUploadDialog
          policies={policies}
          onClose={() => setUploadOpen(false)}
          onUploaded={() => setPage(1)}
        />
      ) : null}

      <ConfirmDialog
        open={Boolean(confirmTarget)}
        onOpenChange={(open) => {
          if (!open) {
            setConfirmTarget(null);
          }
        }}
        title={confirmCopy.title}
        desc={confirmDescription}
        destructive={confirmCopy.destructive}
        isLoading={confirmMutation.isPending}
        confirmText={confirmMutation.isPending ? "处理中…" : confirmCopy.confirmText}
        handleConfirm={() => {
          if (confirmTarget) {
            confirmMutation.mutate(confirmTarget);
          }
        }}
      />
    </ConsolePage>
  );
}

function confirmDescriptionFor(target: ConfirmTarget): string {
  const name = storageFileName(target.file);
  if (target.action === "trash") {
    return `确定将「${name}」移入回收站吗？回收站文件在保留期内可以恢复。`;
  }
  if (target.action === "restore") {
    return `确定恢复「${name}」吗？恢复后文件将重新可下载。`;
  }
  return `确定彻底删除「${name}」吗？文件对象与记录会被永久删除，且无法恢复。`;
}

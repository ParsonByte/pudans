"use client";

import { useMemo, useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, Lock, Pencil, Plus, RefreshCw, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";
import { DataTablePagination, DataTableToolbar, LongText } from "@/components/data-table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
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

import { type ExtensionRecord, isImmutableExtension } from "../lib/extension-records";
import {
  EXTENSION_RESOURCES,
  type ExtensionResourceKind,
  deleteExtensionResource,
  extensionResourceDefinition,
  isDeletableExtensionResource,
  listExtensionResources,
  resolveExtensionResourceError,
  summarizeExtensionResource,
} from "../lib/extension-resources";
import { ExtensionResourceDialog } from "./extension-resource-dialog";

type DialogState = { mode: "create" } | { mode: "edit"; record: ExtensionRecord };

export function ExtensionResourcePanel() {
  const queryClient = useQueryClient();
  const [kind, setKind] = useState<ExtensionResourceKind>("webhooks");
  const [targetRef, setTargetRef] = useState("");
  const [targetRefDraft, setTargetRefDraft] = useState("");
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [dialog, setDialog] = useState<DialogState | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<ExtensionRecord | null>(null);

  const definition = extensionResourceDefinition(kind);
  const requiresTargetRef = kind === "annotationsettings";
  const normalizedTargetRef = targetRef.trim();
  const listEnabled = !requiresTargetRef || normalizedTargetRef.length > 0;

  const listQuery = useQuery({
    queryKey: ["extension-resources", kind, requiresTargetRef ? normalizedTargetRef : ""],
    queryFn: () => listExtensionResources(kind, normalizedTargetRef),
    enabled: listEnabled,
    retry: false,
  });

  const deleteMutation = useMutation({
    mutationFn: (record: ExtensionRecord) => deleteExtensionResource(kind, record.metadata.name),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["extension-resources"] });
      setDeleteTarget(null);
      toast.success("扩展资源已删除。");
    },
    onError: (error) => {
      toast.error(resolveExtensionResourceError(error));
    },
  });

  const filteredItems = useMemo(() => {
    const term = search.trim().toLowerCase();

    return (listQuery.data ?? [])
      .map((record) => {
        const summary = summarizeExtensionResource(kind, record);
        return { record, summary };
      })
      .filter(({ record, summary }) => {
        if (!term) {
          return true;
        }
        return [record.metadata.name, summary.summary, summary.detail, ...summary.badges]
          .filter(Boolean)
          .join("\n")
          .toLowerCase()
          .includes(term);
      });
  }, [kind, listQuery.data, search]);

  const pageCount = Math.max(1, Math.ceil(filteredItems.length / pageSize));
  const currentPage = Math.min(page, pageCount);
  const pageItems = filteredItems.slice((currentPage - 1) * pageSize, currentPage * pageSize);

  const handleKindChange = (value: string) => {
    setKind(value as ExtensionResourceKind);
    setTargetRef("");
    setTargetRefDraft("");
    setSearch("");
    setPage(1);
    setDialog(null);
    setDeleteTarget(null);
  };

  const applyTargetRef = () => {
    setTargetRef(targetRefDraft.trim());
    setPage(1);
  };

  return (
    <div className="space-y-4">
      <DataTableToolbar
        searchPlaceholder="搜索名称或摘要..."
        searchValue={search}
        onSearchChange={(value) => {
          setSearch(value);
          setPage(1);
        }}
        filters={
          <>
            <Select value={kind} onValueChange={handleKindChange}>
              <SelectTrigger className="h-8 w-56" aria-label="资源类型">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {EXTENSION_RESOURCES.map((resource) => (
                  <SelectItem key={resource.plural} value={resource.plural}>
                    {resource.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {requiresTargetRef ? (
              <>
                <Input
                  value={targetRefDraft}
                  onChange={(event) => setTargetRefDraft(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter") {
                      event.preventDefault();
                      applyTargetRef();
                    }
                  }}
                  placeholder="目标引用，例如 content.pudans.tolim.cn/Post"
                  className="h-8 w-72 font-mono"
                  autoComplete="off"
                  spellCheck={false}
                  aria-label="注解设置目标引用"
                />
                <Button type="button" variant="outline" className="h-8" onClick={applyTargetRef}>
                  查询
                </Button>
              </>
            ) : null}
          </>
        }
        isFiltered={
          search.trim().length > 0 ||
          (requiresTargetRef && (targetRef.length > 0 || targetRefDraft.length > 0))
        }
        onReset={() => {
          setSearch("");
          setPage(1);
          if (requiresTargetRef) {
            setTargetRef("");
            setTargetRefDraft("");
          }
        }}
        viewOptions={
          <div className="flex items-center gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => void listQuery.refetch()}
              disabled={listQuery.isFetching || !listEnabled}
            >
              {listQuery.isFetching ? <Loader2 className="animate-spin" /> : <RefreshCw />}
              刷新
            </Button>
            <Button type="button" size="sm" onClick={() => setDialog({ mode: "create" })}>
              <Plus />
              新建
            </Button>
          </div>
        }
      />

      <p className="text-xs leading-5 text-muted-foreground">{definition.description}</p>

      <ConsoleSection>
        {!listEnabled ? (
          <ConsoleState
            variant="empty"
            title="请输入目标引用"
            description="注解设置按目标引用查询列表，请输入 group/kind 后加载。"
          />
        ) : listQuery.isPending ? (
          <Table>
            <TableBody>
              <ConsoleTableSkeleton columns={4} rows={5} cellClassName="px-5 py-3" />
            </TableBody>
          </Table>
        ) : listQuery.isError ? (
          <ConsoleState
            variant="error"
            title="无法加载扩展资源"
            description={resolveExtensionResourceError(listQuery.error)}
            onRetry={() => void listQuery.refetch()}
          />
        ) : pageItems.length === 0 ? (
          <ConsoleState
            variant="empty"
            title={search.trim() ? "没有匹配的扩展资源。" : "暂无扩展资源。"}
            description={search.trim() ? undefined : `点击「新建」添加${definition.label}。`}
          />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="px-5">资源</TableHead>
                <TableHead className="px-5">摘要</TableHead>
                <TableHead className="px-5">状态</TableHead>
                <TableHead className="px-5 text-end">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {pageItems.map(({ record, summary }) => {
                const immutable = isImmutableExtension(record);
                const deletable = isDeletableExtensionResource(kind, record);

                return (
                  <TableRow key={record.metadata.name}>
                    <TableCell className="px-5 py-3">
                      <div className="min-w-0 space-y-1">
                        <LongText className="max-w-64 text-sm font-medium">
                          {record.metadata.name || "未命名资源"}
                        </LongText>
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="text-xs text-muted-foreground">
                            {definition.kindName}
                          </span>
                          {immutable ? (
                            <Badge variant="secondary">
                              <Lock className="size-3" />
                              内置
                            </Badge>
                          ) : null}
                        </div>
                      </div>
                    </TableCell>
                    <TableCell className="px-5 py-3">
                      <div className="min-w-0 space-y-1">
                        <LongText className="max-w-80 text-sm">{summary.summary}</LongText>
                        <LongText className="max-w-80 text-xs text-muted-foreground">
                          {summary.detail}
                        </LongText>
                      </div>
                    </TableCell>
                    <TableCell className="px-5 py-3">
                      <div className="flex flex-wrap items-center gap-2">
                        {summary.badges.map((badge) => (
                          <Badge key={badge} variant="outline">
                            {badge}
                          </Badge>
                        ))}
                      </div>
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
                            disabled={!deletable}
                            title={deletable ? undefined : "该配置由系统管理，不能删除。"}
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
        disabled={listQuery.isFetching}
      />

      {dialog ? (
        <ExtensionResourceDialog
          kind={kind}
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
        title="删除扩展资源"
        desc={
          deleteTarget
            ? `确定删除 ${definition.kindName}「${deleteTarget.metadata.name}」？此操作无法恢复。`
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

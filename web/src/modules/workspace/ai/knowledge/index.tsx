import { useMemo, useState } from "react";

import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";
import {
  ConsoleEmpty,
  ConsolePage,
  ConsoleSection,
} from "@/modules/workspace/console/components/console-page";
import { useAllowedOperations } from "@/modules/workspace/lib/permissions";

import { KnowledgeBaseDetail } from "./components/knowledge-base-detail";
import { KnowledgeBaseDialog } from "./components/knowledge-base-dialog";
import { KnowledgeBaseList } from "./components/knowledge-base-list";
import {
  type KnowledgeBaseItem,
  type KnowledgeBaseStatus,
  deleteKnowledgeBase,
  fetchKnowledgeBaseDocumentCount,
  fetchKnowledgeBases,
  resolveKnowledgeBaseApiError,
} from "./lib/api";
import { matchesKnowledgeBase } from "./lib/model";

const EMPTY_KNOWLEDGE_BASES: KnowledgeBaseItem[] = [];

export function KnowledgeView() {
  const queryClient = useQueryClient();
  const allowedOperations = useAllowedOperations();
  const canCreate = allowedOperations.has("create");
  const canUpdate = allowedOperations.has("update");
  const canDelete = allowedOperations.has("delete");
  const canUpload = allowedOperations.has("upload");
  const canReindex = allowedOperations.has("reindex");
  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState<KnowledgeBaseStatus | "">("");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [selectedName, setSelectedName] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<KnowledgeBaseItem | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<KnowledgeBaseItem | null>(null);

  const knowledgeBasesQuery = useQuery({
    queryKey: ["knowledge-bases"],
    queryFn: fetchKnowledgeBases,
    retry: false,
  });

  const items = knowledgeBasesQuery.data ?? EMPTY_KNOWLEDGE_BASES;
  const normalizedKeyword = keyword.trim().toLowerCase();
  const filteredItems = useMemo(
    () =>
      items.filter((item) => {
        if (status && (item.spec.status ?? "ENABLED") !== status) {
          return false;
        }
        return matchesKnowledgeBase(item, normalizedKeyword);
      }),
    [items, normalizedKeyword, status],
  );
  const pageCount = Math.max(1, Math.ceil(filteredItems.length / pageSize));
  const currentPage = Math.min(page, pageCount);
  const visibleItems = filteredItems.slice((currentPage - 1) * pageSize, currentPage * pageSize);
  const selected =
    (selectedName ? items.find((item) => item.metadata.name === selectedName) : undefined) ??
    visibleItems[0] ??
    (filteredItems.length === items.length ? (items[0] ?? null) : null);

  const documentCountQueries = useQueries({
    queries: visibleItems.map((item) => ({
      queryKey: ["knowledge-base-document-count", item.metadata.name],
      queryFn: () => fetchKnowledgeBaseDocumentCount(item.metadata.name),
      retry: false,
      staleTime: 30_000,
    })),
  });
  const documentCounts = new Map(
    visibleItems.map((item, index) => [item.metadata.name, documentCountQueries[index]?.data]),
  );

  const deleteMutation = useMutation({
    mutationFn: (item: KnowledgeBaseItem) => deleteKnowledgeBase(item.metadata.name),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["knowledge-bases"] });
      await queryClient.invalidateQueries({ queryKey: ["knowledge-base-document-count"] });
      setSelectedName(null);
      setDeleteTarget(null);
      toast.success("知识库已删除。");
    },
    onError: (error) => {
      setDeleteTarget(null);
      toast.error(resolveKnowledgeBaseApiError(error));
    },
  });

  const refresh = () => {
    void knowledgeBasesQuery.refetch();
    void queryClient.invalidateQueries({ queryKey: ["knowledge-base-document-count"] });
  };

  return (
    <ConsolePage title="知识库">
      <div className="grid flex-1 gap-4 xl:grid-cols-[minmax(0,380px)_minmax(0,1fr)] xl:items-start">
        <KnowledgeBaseList
          items={visibleItems}
          total={filteredItems.length}
          allTotal={items.length}
          page={currentPage}
          pageSize={pageSize}
          pageCount={pageCount}
          loading={knowledgeBasesQuery.isPending}
          refreshing={knowledgeBasesQuery.isFetching}
          error={knowledgeBasesQuery.error}
          keyword={keyword}
          status={status}
          documentCounts={documentCounts}
          selectedName={selected?.metadata.name}
          canCreate={canCreate}
          onKeywordChange={(value) => {
            setKeyword(value);
            setPage(1);
          }}
          onStatusChange={(value) => {
            setStatus(value);
            setPage(1);
          }}
          onPageChange={setPage}
          onPageSizeChange={(value) => {
            setPageSize(value);
            setPage(1);
          }}
          onSelect={setSelectedName}
          onCreate={() => setCreateOpen(true)}
          onRefresh={refresh}
          onResetFilters={() => {
            setKeyword("");
            setStatus("");
            setPage(1);
          }}
        />

        {selected ? (
          <KnowledgeBaseDetail
            key={selected.metadata.name}
            knowledgeBase={selected}
            canUpdate={canUpdate}
            canDelete={canDelete}
            canReindex={canReindex}
            canUpload={canUpload}
            onEdit={() => setEditing(selected)}
            onDelete={() => setDeleteTarget(selected)}
          />
        ) : (
          <ConsoleSection className="flex min-h-64 items-center justify-center">
            <ConsoleEmpty>选择左侧的知识库，管理文档、测试检索并查看查询日志。</ConsoleEmpty>
          </ConsoleSection>
        )}
      </div>

      {createOpen ? (
        <KnowledgeBaseDialog
          onClose={() => setCreateOpen(false)}
          onSaved={(saved) => setSelectedName(saved.metadata.name)}
        />
      ) : null}

      {editing ? (
        <KnowledgeBaseDialog
          knowledgeBase={editing}
          onClose={() => setEditing(null)}
          onSaved={(saved) => setSelectedName(saved.metadata.name)}
        />
      ) : null}

      <ConfirmDialog
        open={Boolean(deleteTarget)}
        onOpenChange={(open) => {
          if (!open) {
            setDeleteTarget(null);
          }
        }}
        title="删除知识库"
        desc={
          deleteTarget
            ? `确定删除「${deleteTarget.spec.displayName || deleteTarget.metadata.name}」吗？删除后其文档与索引记录不会自动清理，且无法再通过本页管理。`
            : ""
        }
        destructive
        isLoading={deleteMutation.isPending}
        confirmText={deleteMutation.isPending ? "删除中…" : "删除"}
        handleConfirm={() => {
          if (deleteTarget) {
            deleteMutation.mutate(deleteTarget);
          }
        }}
      />
    </ConsolePage>
  );
}

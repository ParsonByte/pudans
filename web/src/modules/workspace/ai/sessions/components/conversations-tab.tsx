import { useMemo, useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";
import { DataTableToolbar } from "@/components/data-table";
import { Button } from "@/components/ui/button";
import { CursorPager } from "@/modules/workspace/ai/memory/components/cursor-pager";
import {
  MemoryErrorState,
  MemoryLoadingState,
} from "@/modules/workspace/ai/memory/components/memory-states";
import {
  ConsoleEmpty,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";

import {
  type ChatConversation,
  deleteConversation,
  fetchConversations,
  resolveAiSessionApiError,
} from "../lib/api";
import { DEFAULT_SESSION_PAGE_SIZE, conversationTitle } from "../lib/model";
import { useCursorPagination } from "../lib/use-cursor-pagination";
import { ConversationDetailDialog } from "./conversation-detail-dialog";
import { ConversationTable } from "./conversation-table";

type ConversationsTabProps = {
  canDelete: boolean;
};

export function ConversationsTab({ canDelete }: ConversationsTabProps) {
  const queryClient = useQueryClient();
  const pagination = useCursorPagination(DEFAULT_SESSION_PAGE_SIZE);
  const [keyword, setKeyword] = useState("");
  const [detail, setDetail] = useState<ChatConversation | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<ChatConversation | null>(null);

  const conversationsQuery = useQuery({
    queryKey: ["ai-sessions", "conversations", pagination.pageSize, pagination.cursor ?? "start"],
    queryFn: () => fetchConversations({ limit: pagination.pageSize, beforeId: pagination.cursor }),
    retry: false,
  });

  const items = useMemo(() => conversationsQuery.data ?? [], [conversationsQuery.data]);
  const normalizedKeyword = keyword.trim().toLowerCase();
  const visibleItems = useMemo(
    () =>
      normalizedKeyword
        ? items.filter((conversation) =>
            conversationTitle(conversation).toLowerCase().includes(normalizedKeyword),
          )
        : items,
    [items, normalizedKeyword],
  );

  const deleteMutation = useMutation({
    mutationFn: (conversation: ChatConversation) => deleteConversation(conversation.id),
    onSuccess: async (_result, conversation) => {
      await queryClient.invalidateQueries({ queryKey: ["ai-sessions"] });
      await queryClient.invalidateQueries({ queryKey: ["chat-conversations"] });
      setDeleteTarget(null);
      if (detail?.id === conversation.id) {
        setDetail(null);
      }
      if (items.length === 1 && pagination.page > 0) {
        pagination.goPrevious();
      }
      toast.success("会话已删除。");
    },
    onError: (error) => {
      setDeleteTarget(null);
      toast.error(resolveAiSessionApiError(error));
    },
  });

  const refreshing = conversationsQuery.isFetching;
  const hasNext = items.length === pagination.pageSize;

  return (
    <div className="flex flex-col gap-5">
      <ConsoleSection className="overflow-hidden">
        <ConsoleSectionHeader
          title="对话会话"
          description="当前账号的 AI 对话会话，按 ID 倒序分页。标题搜索仅筛选当前页。"
          action={
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => void conversationsQuery.refetch()}
              disabled={refreshing}
            >
              {refreshing ? <Loader2 className="animate-spin" /> : <RefreshCw />}
              刷新
            </Button>
          }
        />

        <div className="border-b px-5 py-4">
          <DataTableToolbar
            searchPlaceholder="搜索本页会话标题"
            searchValue={keyword}
            onSearchChange={setKeyword}
            isFiltered={normalizedKeyword.length > 0}
            onReset={() => setKeyword("")}
          />
        </div>

        {conversationsQuery.isPending ? (
          <MemoryLoadingState rows={5} />
        ) : conversationsQuery.isError ? (
          <MemoryErrorState
            title="无法加载会话"
            message={resolveAiSessionApiError(conversationsQuery.error)}
            onRetry={() => void conversationsQuery.refetch()}
          />
        ) : items.length === 0 ? (
          <ConsoleEmpty>还没有会话。在聊天页面发起对话后，会话会出现在这里。</ConsoleEmpty>
        ) : visibleItems.length === 0 ? (
          <ConsoleEmpty>本页没有匹配的会话标题，可翻页查找或清空搜索。</ConsoleEmpty>
        ) : (
          <ConversationTable
            conversations={visibleItems}
            canDelete={canDelete}
            onOpen={setDetail}
            onDelete={setDeleteTarget}
          />
        )}

        {conversationsQuery.isSuccess && items.length > 0 ? (
          <CursorPager
            page={pagination.page}
            pageSize={pagination.pageSize}
            hasNext={hasNext}
            itemCount={visibleItems.length}
            loading={refreshing}
            onPrevious={pagination.goPrevious}
            onNext={() => pagination.goNext(items.at(-1)?.id)}
            onPageSizeChange={pagination.setPageSize}
          />
        ) : null}
      </ConsoleSection>

      <ConversationDetailDialog conversation={detail} onClose={() => setDetail(null)} />

      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => {
          if (!open && !deleteMutation.isPending) {
            setDeleteTarget(null);
          }
        }}
        title="删除会话"
        desc={
          deleteTarget
            ? `确定删除「${conversationTitle(deleteTarget)}」？删除后会话将从列表移除，且无法在控制台恢复。`
            : ""
        }
        destructive
        confirmText="删除"
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

import { useState } from "react";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Loader2, Pencil, RefreshCw, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { ConsoleSection } from "@/modules/workspace/console/components/console-page";

import {
  type KnowledgeBaseItem,
  type KnowledgeBaseStatus,
  reindexKnowledgeBase,
  resolveKnowledgeBaseApiError,
  updateKnowledgeBase,
} from "../lib/api";
import {
  chunkStrategyLabel,
  knowledgeBaseCode,
  knowledgeBaseStatusLabel,
  visibilityLabel,
} from "../lib/model";
import { DocumentPanel } from "./document-panel";
import { QueryLogPanel } from "./query-log-panel";
import { RetrievalPanel } from "./retrieval-panel";

type KnowledgeBaseTab = "documents" | "retrieval" | "query-logs";

type KnowledgeBaseDetailProps = {
  knowledgeBase: KnowledgeBaseItem;
  canUpdate: boolean;
  canDelete: boolean;
  canReindex: boolean;
  canUpload: boolean;
  onEdit: () => void;
  onDelete: () => void;
};

export function KnowledgeBaseDetail({
  knowledgeBase,
  canUpdate,
  canDelete,
  canReindex,
  canUpload,
  onEdit,
  onDelete,
}: KnowledgeBaseDetailProps) {
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<KnowledgeBaseTab>("documents");
  const enabled = knowledgeBase.spec.status !== "DISABLED";

  const toggleMutation = useMutation({
    mutationFn: (status: KnowledgeBaseStatus) =>
      updateKnowledgeBase({
        ...knowledgeBase,
        spec: { ...knowledgeBase.spec, status },
      }),
    onSuccess: (saved) => {
      queryClient.setQueryData<KnowledgeBaseItem[]>(["knowledge-bases"], (items) =>
        items?.map((item) => (item.metadata.name === saved.metadata.name ? saved : item)),
      );
      toast.success(saved.spec.status === "DISABLED" ? "知识库已停用。" : "知识库已启用。");
    },
    onError: (error) => toast.error(resolveKnowledgeBaseApiError(error)),
  });

  const reindexMutation = useMutation({
    mutationFn: () => reindexKnowledgeBase(knowledgeBase.metadata.name),
    onSuccess: async (result) => {
      await queryClient.invalidateQueries({ queryKey: ["knowledge-base-documents"] });
      await queryClient.invalidateQueries({ queryKey: ["knowledge-base-document-count"] });
      if (result.queued > 0) {
        toast.success(`已提交 ${result.queued} 个文档重建索引。`);
      } else {
        toast.info("没有可重建索引的就绪文档。");
      }
    },
    onError: (error) => toast.error(resolveKnowledgeBaseApiError(error)),
  });

  return (
    <ConsoleSection className="flex min-h-[520px] flex-col overflow-hidden">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b px-5 py-4">
        <div className="min-w-0 space-y-1">
          <div className="flex flex-wrap items-center gap-2">
            <h2 className="truncate text-base font-semibold">
              {knowledgeBase.spec.displayName || knowledgeBase.metadata.name}
            </h2>
            <Badge variant={enabled ? "secondary" : "outline"}>
              {knowledgeBaseStatusLabel(knowledgeBase.spec.status)}
            </Badge>
            <Badge variant="outline">{visibilityLabel(knowledgeBase.spec.visibility)}可见</Badge>
          </div>
          <p className="truncate font-mono text-[11px] text-muted-foreground">
            {knowledgeBaseCode(knowledgeBase.metadata.name)} · {knowledgeBase.spec.embeddingModel} ·{" "}
            {chunkStrategyLabel(knowledgeBase.spec.chunkStrategy)}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Tooltip>
            <TooltipTrigger asChild>
              <div className="flex items-center gap-2 text-xs text-muted-foreground">
                <span>{enabled ? "已启用" : "已停用"}</span>
                <Switch
                  checked={enabled}
                  disabled={!canUpdate || toggleMutation.isPending}
                  onCheckedChange={(checked) =>
                    toggleMutation.mutate(checked ? "ENABLED" : "DISABLED")
                  }
                  aria-label={enabled ? "停用知识库" : "启用知识库"}
                />
              </div>
            </TooltipTrigger>
            <TooltipContent className="max-w-xs">
              停用后该知识库将从可读列表隐藏，检索与聊天注入都不会命中。
            </TooltipContent>
          </Tooltip>
          {canReindex ? (
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={reindexMutation.isPending}
              onClick={() => reindexMutation.mutate()}
            >
              {reindexMutation.isPending ? <Loader2 className="animate-spin" /> : <RefreshCw />}
              重建索引
            </Button>
          ) : null}
          {canUpdate ? (
            <Button type="button" variant="outline" size="sm" onClick={onEdit}>
              <Pencil />
              编辑
            </Button>
          ) : null}
          {canDelete ? (
            <Button
              type="button"
              variant="destructive"
              size="icon-sm"
              aria-label="删除知识库"
              onClick={onDelete}
            >
              <Trash2 />
            </Button>
          ) : null}
        </div>
      </div>

      <Tabs
        value={tab}
        onValueChange={(value) => setTab(value as KnowledgeBaseTab)}
        className="flex flex-1 flex-col gap-0 px-5 pt-2 pb-5"
      >
        <TabsList variant="line" className="h-10 w-full justify-start border-b">
          <TabsTrigger value="documents" className="flex-none px-3">
            文档
          </TabsTrigger>
          <TabsTrigger value="retrieval" className="flex-none px-3">
            检索测试
          </TabsTrigger>
          <TabsTrigger value="query-logs" className="flex-none px-3">
            查询日志
          </TabsTrigger>
        </TabsList>
        <TabsContent value="documents" className="mt-4">
          <DocumentPanel
            knowledgeBaseName={knowledgeBase.metadata.name}
            canUpload={canUpload}
            canDelete={canDelete}
          />
        </TabsContent>
        <TabsContent value="retrieval" className="mt-4">
          <RetrievalPanel knowledgeBase={knowledgeBase} />
        </TabsContent>
        <TabsContent value="query-logs" className="mt-4">
          <QueryLogPanel knowledgeBaseName={knowledgeBase.metadata.name} />
        </TabsContent>
      </Tabs>
    </ConsoleSection>
  );
}

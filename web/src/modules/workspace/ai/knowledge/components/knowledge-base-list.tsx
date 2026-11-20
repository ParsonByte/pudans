import { ChevronRight, Loader2, Plus, RefreshCw, Search } from "lucide-react";

import { DataTablePagination } from "@/components/data-table";
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
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { ConsoleEmpty, ConsoleSection } from "@/modules/workspace/console/components/console-page";

import {
  type KnowledgeBaseItem,
  type KnowledgeBaseStatus,
  resolveKnowledgeBaseApiError,
} from "../lib/api";
import {
  KNOWLEDGE_BASE_STATUS_OPTIONS,
  chunkStrategyLabel,
  knowledgeBaseCode,
  knowledgeBaseStatusLabel,
  visibilityLabel,
} from "../lib/model";
import { KnowledgeErrorState } from "./knowledge-states";

type KnowledgeBaseListProps = {
  items: KnowledgeBaseItem[];
  total: number;
  allTotal: number;
  page: number;
  pageSize: number;
  pageCount: number;
  loading: boolean;
  refreshing: boolean;
  error: unknown;
  keyword: string;
  status: KnowledgeBaseStatus | "";
  documentCounts: Map<string, number | undefined>;
  selectedName?: string;
  canCreate: boolean;
  onKeywordChange: (value: string) => void;
  onStatusChange: (value: KnowledgeBaseStatus | "") => void;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
  onSelect: (name: string) => void;
  onCreate: () => void;
  onRefresh: () => void;
  onResetFilters: () => void;
};

export function KnowledgeBaseList({
  items,
  total,
  allTotal,
  page,
  pageSize,
  pageCount,
  loading,
  refreshing,
  error,
  keyword,
  status,
  documentCounts,
  selectedName,
  canCreate,
  onKeywordChange,
  onStatusChange,
  onPageChange,
  onPageSizeChange,
  onSelect,
  onCreate,
  onRefresh,
  onResetFilters,
}: KnowledgeBaseListProps) {
  return (
    <div className="flex min-w-0 flex-col gap-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <h2 className="text-base font-semibold">知识库</h2>
          <p className="text-xs text-muted-foreground">
            {allTotal} 个可读知识库，当前筛选 {total} 个。
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button type="button" variant="outline" size="icon" onClick={onRefresh} aria-label="刷新">
            {refreshing ? <Loader2 className="animate-spin" /> : <RefreshCw />}
          </Button>
          {canCreate ? (
            <Button type="button" onClick={onCreate}>
              <Plus />
              新建知识库
            </Button>
          ) : null}
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <div className="relative min-w-0 flex-1">
          <Search className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={keyword}
            onChange={(event) => onKeywordChange(event.target.value)}
            placeholder="搜索名称、描述或向量模型"
            className="ps-9"
            aria-label="搜索知识库"
          />
        </div>
        <Select
          value={status || "ALL"}
          onValueChange={(value) =>
            onStatusChange(value === "ALL" ? "" : (value as KnowledgeBaseStatus))
          }
        >
          <SelectTrigger className="w-32" aria-label="按状态筛选">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {KNOWLEDGE_BASE_STATUS_OPTIONS.map((option) => (
              <SelectItem key={option.value || "ALL"} value={option.value || "ALL"}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {loading ? (
        <div className="grid gap-3">
          {Array.from({ length: 4 }, (_, index) => (
            <Skeleton key={index} className="h-24 rounded-xl" />
          ))}
        </div>
      ) : error ? (
        <KnowledgeErrorState
          title="无法加载知识库"
          message={resolveKnowledgeBaseApiError(error)}
          onRetry={onRefresh}
        />
      ) : items.length === 0 ? (
        <ConsoleSection>
          {allTotal === 0 ? (
            <div className="flex flex-col items-center gap-3 px-5 py-10 text-center">
              <ConsoleEmpty>还没有可读的知识库。创建知识库后即可上传文档并测试检索。</ConsoleEmpty>
              {canCreate ? (
                <Button type="button" onClick={onCreate}>
                  <Plus />
                  新建知识库
                </Button>
              ) : null}
            </div>
          ) : (
            <div className="flex flex-col items-center gap-3 px-5 py-10 text-center">
              <ConsoleEmpty>没有匹配的知识库。</ConsoleEmpty>
              <Button type="button" variant="outline" size="sm" onClick={onResetFilters}>
                清除筛选
              </Button>
            </div>
          )}
        </ConsoleSection>
      ) : (
        <div className="grid gap-2">
          {items.map((item) => {
            const enabled = item.spec.status !== "DISABLED";
            const selected = item.metadata.name === selectedName;
            return (
              <button
                key={item.metadata.name}
                type="button"
                aria-pressed={selected}
                onClick={() => onSelect(item.metadata.name)}
                className={cn(
                  "group flex w-full flex-col gap-3 rounded-xl border bg-card p-4 text-start transition-colors hover:bg-accent/30 focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none",
                  selected && "border-primary/60 bg-accent/40 ring-1 ring-primary/30",
                )}
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0 space-y-1">
                    <div className="flex flex-wrap items-center gap-1.5">
                      <span className="truncate text-sm font-semibold">
                        {item.spec.displayName || item.metadata.name}
                      </span>
                      <Badge variant={enabled ? "secondary" : "outline"}>
                        {knowledgeBaseStatusLabel(item.spec.status)}
                      </Badge>
                    </div>
                    <p className="line-clamp-2 text-xs leading-5 text-muted-foreground">
                      {item.spec.description || "未填写描述。"}
                    </p>
                  </div>
                  <ChevronRight className="mt-1 size-4 shrink-0 text-muted-foreground transition-transform group-hover:translate-x-0.5" />
                </div>
                <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-muted-foreground">
                  <span>{visibilityLabel(item.spec.visibility)}可见</span>
                  <span>{chunkStrategyLabel(item.spec.chunkStrategy)}</span>
                  <span>{documentCounts.get(item.metadata.name) ?? "—"} 个文档</span>
                </div>
                <div className="flex flex-wrap items-center gap-x-3 gap-y-1 font-mono text-[10px] text-muted-foreground">
                  <span className="truncate">{knowledgeBaseCode(item.metadata.name)}</span>
                  <span className="truncate">{item.spec.embeddingModel}</span>
                </div>
              </button>
            );
          })}
        </div>
      )}

      {!loading && !error && total > 0 ? (
        <DataTablePagination
          currentPage={page}
          pageCount={pageCount}
          pageSize={pageSize}
          onPageChange={onPageChange}
          onPageSizeChange={onPageSizeChange}
          disabled={refreshing}
        />
      ) : null}
    </div>
  );
}

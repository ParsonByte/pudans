import { useMemo, useState } from "react";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { RotateCcw, Search, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";

import type {
  MemoryAssetView,
  MemoryL0Item,
  MemoryL1Item,
  MemoryL2Item,
  MemoryLayer,
  MemoryLayerCounts,
} from "../lib/api";
import {
  deleteMemoryMessages,
  deleteMemoryRecords,
  fetchMemorySceneBody,
  resolveMemoryApiError,
  updateMemoryLayerL1,
  updateMemoryLayerL2,
  updateMemoryLayerL3,
} from "../lib/api";
import {
  MEMORY_LAYER_META,
  MEMORY_LAYER_PAGE_SIZE,
  MEMORY_TIME_FILTER_LAYERS,
  defaultMemoryTimeRange,
  memoryItemContent,
  sortMemoryItemsAscending,
  stripScenarioMeta,
  toIsoDateTime,
} from "../lib/model";
import { invalidateMemoryAssetQueries, useMemoryLayerPage, useMemoryProfile } from "../lib/queries";
import { LayerEditDialog } from "./layer-edit-dialog";
import { MemoryConfirmDialog } from "./memory-confirm-dialogs";
import {
  MemoryAtomicView,
  MemoryChatView,
  MemoryProfileView,
  MemorySceneView,
} from "./memory-layer-views";
import { MemoryEmptyState, MemoryErrorState, MemoryLayerSkeleton } from "./memory-states";

type MemoryLayerContentProps = {
  asset: MemoryAssetView;
  team: string;
  counts?: MemoryLayerCounts;
  isOwner: boolean;
};

type AppliedFilters = {
  from?: string;
  to?: string;
  q?: string;
};

type LayerEditState = {
  layer: MemoryLayer;
  id?: number;
  initialContent: string;
};

type LayerEditInput = {
  layer: MemoryLayer;
  id?: number;
  content: string;
};

type DeleteState = {
  layer: "L0" | "L1";
  ids: number[];
};

const LAYER_EDIT_COPY: Record<
  MemoryLayer,
  { title: string; description: string; hint: string; allowEmpty: boolean }
> = {
  L0: {
    title: "编辑对话消息",
    description: "L0 对话消息不支持编辑。",
    hint: "",
    allowEmpty: false,
  },
  L1: {
    title: "编辑原子记忆",
    description: "修改 L1 原子记忆正文。",
    hint: "保存后立即生效，并影响后续场景与画像聚合。",
    allowEmpty: false,
  },
  L2: {
    title: "编辑场景记忆",
    description: "修改 L2 场景摘要正文。",
    hint: "META 信息（如 [META] 块）展示时会隐藏，保存将覆盖场景正文。",
    allowEmpty: false,
  },
  L3: {
    title: "编辑核心画像",
    description: "修改 L3 长期核心画像，支持 Markdown。",
    hint: "保存后将作为 Agent 的长期核心画像注入。",
    allowEmpty: true,
  },
};

export function MemoryLayerContent({ asset, team, counts, isOwner }: MemoryLayerContentProps) {
  const queryClient = useQueryClient();
  const initialRange = useMemo(() => defaultMemoryTimeRange(), []);
  const [activeLayer, setActiveLayer] = useState<MemoryLayer>("L1");
  const [mode, setMode] = useState<"browse" | "search">("browse");
  const [draftFrom, setDraftFrom] = useState(initialRange.from);
  const [draftTo, setDraftTo] = useState(initialRange.to);
  const [draftQuery, setDraftQuery] = useState("");
  const [filters, setFilters] = useState<AppliedFilters>(() => ({
    from: toIsoDateTime(initialRange.from),
    to: toIsoDateTime(initialRange.to),
  }));
  const [selectedIds, setSelectedIds] = useState<ReadonlySet<number>>(new Set());
  const [editState, setEditState] = useState<LayerEditState | null>(null);
  const [deleteState, setDeleteState] = useState<DeleteState | null>(null);

  const layerFilters = {
    from: filters.from,
    to: filters.to,
    q: filters.q,
  };

  const l0Query = useMemoryLayerPage<MemoryL0Item>(
    asset.name,
    team,
    "L0",
    { ...layerFilters, limit: MEMORY_LAYER_PAGE_SIZE.L0 },
    activeLayer === "L0",
  );
  const l1Query = useMemoryLayerPage<MemoryL1Item>(
    asset.name,
    team,
    "L1",
    { ...layerFilters, limit: MEMORY_LAYER_PAGE_SIZE.L1 },
    activeLayer === "L1",
  );
  const l2Query = useMemoryLayerPage<MemoryL2Item>(
    asset.name,
    team,
    "L2",
    { limit: MEMORY_LAYER_PAGE_SIZE.L2 },
    activeLayer === "L2",
  );
  const profileQuery = useMemoryProfile(asset.name, team, activeLayer === "L3");

  const l0Items = useMemo(
    () => sortMemoryItemsAscending(l0Query.data?.pages.flatMap((page) => page.items) ?? []),
    [l0Query.data],
  );
  const l1Items = useMemo(
    () => l1Query.data?.pages.flatMap((page) => page.items) ?? [],
    [l1Query.data],
  );
  const l2Items = useMemo(
    () => l2Query.data?.pages.flatMap((page) => page.items) ?? [],
    [l2Query.data],
  );

  const editMutation = useMutation({
    mutationFn: async (input: LayerEditInput) => {
      if (input.layer === "L1" && input.id !== undefined) {
        await updateMemoryLayerL1(asset.name, team, input.id, input.content);
        return;
      }
      if (input.layer === "L2" && input.id !== undefined) {
        await updateMemoryLayerL2(asset.name, team, input.id, input.content);
        return;
      }
      if (input.layer === "L3") {
        await updateMemoryLayerL3(asset.name, team, input.content);
      }
    },
    onSuccess: async () => {
      await invalidateMemoryAssetQueries(queryClient, asset.name);
      toast.success("记忆内容已保存");
      setEditState(null);
    },
    onError: (error) => {
      toast.error(resolveMemoryApiError(error));
    },
  });

  const deleteMutation = useMutation({
    mutationFn: async (input: DeleteState) => {
      if (input.layer === "L0") {
        await deleteMemoryMessages(asset.name, team, input.ids);
        return;
      }
      await deleteMemoryRecords(asset.name, team, input.ids);
    },
    onSuccess: async () => {
      await invalidateMemoryAssetQueries(queryClient, asset.name);
      toast.success("所选记忆已删除");
      setDeleteState(null);
      setSelectedIds(new Set());
    },
    onError: (error) => {
      toast.error(resolveMemoryApiError(error));
    },
  });

  function toggleSelected(id: number) {
    setSelectedIds((current) => {
      const next = new Set(current);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }

  function applyFilters(nextMode: "browse" | "search" = mode) {
    setFilters({
      from: toIsoDateTime(draftFrom),
      to: toIsoDateTime(draftTo),
      q: nextMode === "search" ? draftQuery.trim() || undefined : undefined,
    });
    setSelectedIds(new Set());
  }

  function resetFilters() {
    const range = defaultMemoryTimeRange();
    setDraftFrom(range.from);
    setDraftTo(range.to);
    setDraftQuery("");
    setFilters({ from: toIsoDateTime(range.from), to: toIsoDateTime(range.to) });
    setSelectedIds(new Set());
  }

  async function openSceneEdit(item: MemoryL2Item) {
    const toastId = toast.loading("加载场景内容…");
    try {
      const body = await fetchMemorySceneBody(asset.name, team, item.path);
      setEditState({
        layer: "L2",
        id: item.id,
        initialContent: stripScenarioMeta(body),
      });
    } catch (error) {
      toast.error(resolveMemoryApiError(error));
    } finally {
      toast.dismiss(toastId);
    }
  }

  const activePageQuery = activeLayer === "L0" ? l0Query : activeLayer === "L1" ? l1Query : l2Query;
  const selectable = isOwner && (activeLayer === "L0" || activeLayer === "L1");
  const isLoading = activeLayer === "L3" ? profileQuery.isLoading : activePageQuery.isLoading;
  const isError = activeLayer === "L3" ? profileQuery.isError : activePageQuery.isError;
  const queryError = activeLayer === "L3" ? profileQuery.error : activePageQuery.error;
  const showFilters = MEMORY_TIME_FILTER_LAYERS.has(activeLayer);
  const editCopy = editState ? LAYER_EDIT_COPY[editState.layer] : undefined;

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="shrink-0 px-4 pt-3">
        <div className="grid grid-cols-4 gap-2">
          {MEMORY_LAYER_META.map((layer) => {
            const active = activeLayer === layer.id;
            const count = counts?.[layer.id] ?? asset.counts?.[layer.id] ?? 0;
            return (
              <button
                key={layer.id}
                type="button"
                aria-pressed={active}
                onClick={() => {
                  setActiveLayer(layer.id);
                  setSelectedIds(new Set());
                }}
                className={`rounded-lg border px-2.5 py-2 text-left transition-colors outline-none focus-visible:ring-2 focus-visible:ring-ring/50 ${
                  active ? "border-primary bg-primary/5" : "border-border bg-card hover:bg-muted/50"
                }`}
              >
                <span className="flex items-center justify-between gap-1">
                  <span className={`text-xs font-medium ${active ? "text-primary" : ""}`}>
                    {layer.label}
                  </span>
                  <span className="font-mono text-[11px] text-muted-foreground">{count}</span>
                </span>
                <span className="mt-0.5 block truncate text-[11px] text-muted-foreground">
                  {layer.description}
                </span>
              </button>
            );
          })}
        </div>
      </div>

      {showFilters ? (
        <div className="flex shrink-0 flex-wrap items-center gap-2 px-4 pt-3">
          <ToggleGroup
            type="single"
            variant="outline"
            size="sm"
            value={mode}
            onValueChange={(value) => {
              if (value !== "browse" && value !== "search") {
                return;
              }
              setMode(value);
              applyFilters(value);
            }}
          >
            <ToggleGroupItem value="browse">浏览</ToggleGroupItem>
            <ToggleGroupItem value="search">搜索</ToggleGroupItem>
          </ToggleGroup>
          {mode === "search" ? (
            <Input
              value={draftQuery}
              onChange={(event) => setDraftQuery(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  applyFilters();
                }
              }}
              placeholder="搜索记忆内容"
              className="h-7 w-40 text-xs"
            />
          ) : null}
          <Input
            type="datetime-local"
            value={draftFrom}
            onChange={(event) => setDraftFrom(event.target.value)}
            aria-label="开始时间"
            className="h-7 w-[170px] text-xs"
          />
          <span className="text-xs text-muted-foreground">至</span>
          <Input
            type="datetime-local"
            value={draftTo}
            onChange={(event) => setDraftTo(event.target.value)}
            aria-label="结束时间"
            className="h-7 w-[170px] text-xs"
          />
          <Button type="button" variant="outline" size="sm" onClick={() => applyFilters()}>
            <Search />
            查询
          </Button>
          <Button type="button" variant="ghost" size="sm" onClick={resetFilters}>
            <RotateCcw />
            重置
          </Button>
          {selectedIds.size > 0 && (activeLayer === "L0" || activeLayer === "L1") ? (
            <Button
              type="button"
              variant="destructive"
              size="sm"
              className="ms-auto"
              onClick={() =>
                setDeleteState({
                  layer: activeLayer,
                  ids: Array.from(selectedIds),
                })
              }
            >
              <Trash2 />
              删除 {selectedIds.size} 项
            </Button>
          ) : null}
        </div>
      ) : null}

      <div className="min-h-0 flex-1 overflow-y-auto px-4 py-3">
        {isLoading ? <MemoryLayerSkeleton layer={activeLayer} /> : null}
        {!isLoading && isError ? (
          <MemoryErrorState
            message={resolveMemoryApiError(queryError)}
            onRetry={() =>
              activeLayer === "L3" ? profileQuery.refetch() : activePageQuery.refetch()
            }
          />
        ) : null}

        {!isLoading && !isError && activeLayer === "L0" ? (
          l0Items.length > 0 || l0Query.hasNextPage ? (
            <div className="space-y-3">
              {l0Query.hasNextPage ? (
                <div className="flex justify-center">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={l0Query.isFetchingNextPage}
                    onClick={() => l0Query.fetchNextPage()}
                  >
                    {l0Query.isFetchingNextPage ? "加载中…" : "加载更早消息"}
                  </Button>
                </div>
              ) : null}
              <MemoryChatView
                items={l0Items}
                selectable={selectable}
                selectedIds={selectedIds}
                onToggleSelect={toggleSelected}
              />
            </div>
          ) : (
            <MemoryEmptyState
              title="暂无对话记忆"
              description="该时间范围内没有 L0 原始对话消息。"
            />
          )
        ) : null}

        {!isLoading && !isError && activeLayer === "L1" ? (
          l1Items.length > 0 || l1Query.hasNextPage ? (
            <div className="space-y-3">
              <MemoryAtomicView
                items={l1Items}
                canEdit={isOwner}
                onEdit={(item) =>
                  setEditState({
                    layer: "L1",
                    id: item.id,
                    initialContent: memoryItemContent(item),
                  })
                }
                selectable={selectable}
                selectedIds={selectedIds}
                onToggleSelect={toggleSelected}
              />
              {l1Query.hasNextPage ? (
                <div className="flex justify-center">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={l1Query.isFetchingNextPage}
                    onClick={() => l1Query.fetchNextPage()}
                  >
                    {l1Query.isFetchingNextPage ? "加载中…" : "加载更多"}
                  </Button>
                </div>
              ) : null}
            </div>
          ) : (
            <MemoryEmptyState
              title="暂无原子记忆"
              description="Agent 对话后会自动抽取 L1 原子记忆。"
            />
          )
        ) : null}

        {!isLoading && !isError && activeLayer === "L2" ? (
          l2Items.length > 0 || l2Query.hasNextPage ? (
            <div className="space-y-3">
              <MemorySceneView
                assetName={asset.name}
                team={team}
                items={l2Items}
                canEdit={isOwner}
                onEdit={(item) => void openSceneEdit(item)}
              />
              {l2Query.hasNextPage ? (
                <div className="flex justify-center">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={l2Query.isFetchingNextPage}
                    onClick={() => l2Query.fetchNextPage()}
                  >
                    {l2Query.isFetchingNextPage ? "加载中…" : "加载更多"}
                  </Button>
                </div>
              ) : null}
            </div>
          ) : (
            <MemoryEmptyState
              title="暂无场景记忆"
              description="原子记忆聚合后将生成 L2 场景摘要。"
            />
          )
        ) : null}

        {!isLoading && !isError && activeLayer === "L3" ? (
          profileQuery.data?.content ? (
            <MemoryProfileView
              content={profileQuery.data.content}
              updatedAt={profileQuery.data.updatedAt}
              canEdit={isOwner}
              onEdit={() =>
                setEditState({
                  layer: "L3",
                  initialContent: profileQuery.data?.content ?? "",
                })
              }
            />
          ) : (
            <MemoryEmptyState
              title="暂无核心画像"
              description="根据长期记忆生成 L3 核心画像，也可手动补充。"
              action={
                isOwner ? (
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => setEditState({ layer: "L3", initialContent: "" })}
                  >
                    编辑核心画像
                  </Button>
                ) : undefined
              }
            />
          )
        ) : null}
      </div>

      {editState && editCopy ? (
        <LayerEditDialog
          key={`${editState.layer}:${editState.id ?? "profile"}`}
          title={editCopy.title}
          description={editCopy.description}
          initialContent={editState.initialContent}
          hint={editCopy.hint}
          pending={editMutation.isPending}
          allowEmpty={editCopy.allowEmpty}
          onSave={(content) =>
            editMutation.mutate({
              layer: editState.layer,
              id: editState.id,
              content,
            })
          }
          onClose={() => setEditState(null)}
        />
      ) : null}

      {deleteState ? (
        <MemoryConfirmDialog
          title="删除所选记忆"
          description={`将永久删除 ${deleteState.ids.length} 条${
            deleteState.layer === "L0" ? "对话消息" : "原子记忆"
          }，删除后不可恢复。`}
          confirmLabel="删除"
          destructive
          pending={deleteMutation.isPending}
          onConfirm={() => deleteMutation.mutate(deleteState)}
          onClose={() => setDeleteState(null)}
        />
      ) : null}
    </div>
  );
}

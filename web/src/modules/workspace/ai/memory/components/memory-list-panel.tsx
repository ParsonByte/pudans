import { Link2, Unlink } from "lucide-react";

import { Button } from "@/components/ui/button";
import { useAgentLabels } from "@/modules/workspace/ai/library/lib/agents";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";

import type { MemoryAssetView } from "../lib/api";
import {
  MEMORY_VISIBILITY_LABELS,
  isMemoryAssetHidden,
  memoryAssetTimestamp,
  memoryAssetTitle,
} from "../lib/model";
import { MemoryBlockListSkeleton, MemoryEmptyState, MemoryErrorState } from "./memory-states";

type MemoryListPanelProps = {
  assets: MemoryAssetView[];
  total: number;
  loading: boolean;
  errorMessage?: string;
  selectedName?: string;
  currentUserId?: string;
  pendingUnbindKey?: string;
  onSelect: (asset: MemoryAssetView) => void;
  onRetry: () => void;
  onUnbind: (asset: MemoryAssetView, agent: string) => void;
};

function memoryAssetCount(asset: MemoryAssetView): number {
  const counts = asset.counts;
  return (counts?.L0 ?? 0) + (counts?.L1 ?? 0) + (counts?.L2 ?? 0) + (counts?.L3 ?? 0);
}

export function MemoryListPanel({
  assets,
  total,
  loading,
  errorMessage,
  selectedName,
  currentUserId,
  pendingUnbindKey,
  onSelect,
  onRetry,
  onUnbind,
}: MemoryListPanelProps) {
  const agentLabels = useAgentLabels();

  return (
    <div className="flex h-full flex-col rounded-xl border bg-card">
      <div className="flex shrink-0 items-center justify-between gap-2 border-b px-4 py-3">
        <div>
          <h2 className="text-sm font-medium">记忆块</h2>
          <p className="text-xs text-muted-foreground">共 {total} 条</p>
        </div>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto p-2">
        {loading ? <MemoryBlockListSkeleton /> : null}
        {!loading && errorMessage ? (
          <MemoryErrorState message={errorMessage} onRetry={onRetry} />
        ) : null}
        {!loading && !errorMessage && assets.length === 0 ? (
          <MemoryEmptyState
            title="暂无记忆块"
            description="切换团队或创建记忆资产后，可在此查看原子记忆块。"
          />
        ) : null}
        {!loading && !errorMessage && assets.length > 0 ? (
          <ul className="space-y-1.5">
            {assets.map((asset) => {
              const hidden = isMemoryAssetHidden(asset, currentUserId);
              const isOwner = Boolean(currentUserId) && asset.ownerUserId === currentUserId;
              const selected = asset.name === selectedName;
              const author = asset.sourceUserId || asset.ownerUserId;
              return (
                <li
                  key={asset.name}
                  data-selected={selected ? "true" : undefined}
                  className={`rounded-lg border transition-colors ${
                    selected
                      ? "border-primary/30 bg-primary/5"
                      : "border-transparent hover:bg-muted/60"
                  } ${hidden ? "opacity-60" : ""}`}
                >
                  <button
                    type="button"
                    disabled={hidden}
                    onClick={() => onSelect(asset)}
                    className="w-full space-y-1.5 px-3.5 pt-3 pb-1 text-left outline-none focus-visible:ring-2 focus-visible:ring-ring/50 disabled:cursor-not-allowed"
                  >
                    <span className="flex items-start justify-between gap-2">
                      <span className="line-clamp-1 text-[13px] font-medium">
                        {memoryAssetTitle(asset)}
                      </span>
                      <span className="shrink-0 rounded-full bg-muted px-2 py-0.5 text-[11px] text-muted-foreground">
                        {memoryAssetCount(asset)}
                      </span>
                    </span>
                    <span className="block truncate font-mono text-[11px] text-muted-foreground">
                      {asset.name}
                    </span>
                    <span className="flex items-center justify-between gap-2 text-[11px] text-muted-foreground">
                      <span className="truncate">
                        {author} · {MEMORY_VISIBILITY_LABELS[asset.visibility]}
                        {asset.status === "DISABLED" ? " · 已停用" : ""}
                      </span>
                      <span className="shrink-0">
                        {formatUserDateTime(memoryAssetTimestamp(asset))}
                      </span>
                    </span>
                  </button>
                  <div className="flex items-center justify-between gap-2 px-3.5 pt-1 pb-2.5">
                    {hidden ? (
                      <span className="text-[11px] text-muted-foreground">仅所有者可查看</span>
                    ) : asset.agents.length > 0 ? (
                      <span className="flex min-w-0 items-center gap-1 text-[11px] text-muted-foreground">
                        <Link2 className="size-3 shrink-0" />
                        <span className="truncate">
                          {asset.agents.map((agent) => agentLabels.get(agent) ?? agent).join("、")}
                        </span>
                      </span>
                    ) : (
                      <span className="text-[11px] text-muted-foreground">未绑定 Agent</span>
                    )}
                    {isOwner && asset.agents.length > 0 && !hidden ? (
                      <span className="flex shrink-0 items-center gap-1">
                        {asset.agents.map((agent) => {
                          const key = `${asset.name}:${agent}`;
                          return (
                            <Button
                              key={agent}
                              type="button"
                              variant="ghost"
                              size="xs"
                              title={`解绑 ${agentLabels.get(agent) ?? agent}`}
                              aria-label={`解绑 ${agentLabels.get(agent) ?? agent}`}
                              disabled={pendingUnbindKey === key}
                              onClick={() => onUnbind(asset, agent)}
                            >
                              <Unlink />
                              解绑
                            </Button>
                          );
                        })}
                      </span>
                    ) : null}
                  </div>
                </li>
              );
            })}
          </ul>
        ) : null}
      </div>
    </div>
  );
}

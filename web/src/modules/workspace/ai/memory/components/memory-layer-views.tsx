import { useState } from "react";

import { ChevronDown, Loader2, Pencil, RotateCw } from "lucide-react";

import { MarkdownContent } from "@/components/content/markdown";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";

import type { MemoryL0Item, MemoryL1Item, MemoryL2Item } from "../lib/api";
import {
  MEMORY_SCOPE_LABELS,
  MEMORY_TYPE_LABELS,
  memoryItemContent,
  stripScenarioMeta,
} from "../lib/model";
import { useMemorySceneBody } from "../lib/queries";

type MemorySelectable = {
  selectable: boolean;
  selectedIds: ReadonlySet<number>;
  onToggleSelect: (id: number) => void;
};

function MemorySelection({
  selectable,
  selectedIds,
  onToggleSelect,
  id,
  label,
}: MemorySelectable & { id: number; label: string }) {
  if (!selectable) {
    return null;
  }
  return (
    <Checkbox
      checked={selectedIds.has(id)}
      onCheckedChange={() => onToggleSelect(id)}
      aria-label={label}
      className="mt-1"
    />
  );
}

const ROLE_LABELS: Record<string, string> = {
  user: "用户",
  assistant: "助手",
  system: "系统",
  tool: "工具",
};

export function MemoryChatView({
  items,
  ...selection
}: { items: MemoryL0Item[] } & MemorySelectable) {
  return (
    <div className="space-y-3">
      {items.map((item) => {
        const role = item.role || "assistant";
        const content = item.content ?? "";
        if (role === "system") {
          return (
            <div key={item.id} className="flex items-center justify-center gap-2">
              <MemorySelection {...selection} id={item.id} label={`选择第 ${item.id} 条系统消息`} />
              <span className="max-w-[85%] bg-muted px-3 py-1 text-xs text-muted-foreground">
                {content}
              </span>
            </div>
          );
        }
        const isUser = role === "user";
        return (
          <div key={item.id} className="flex items-start gap-2">
            <MemorySelection {...selection} id={item.id} label={`选择第 ${item.id} 条消息`} />
            <div className={`flex min-w-0 flex-1 ${isUser ? "justify-end" : "justify-start"}`}>
              <div
                className={`max-w-[75%] px-3 py-2.5 ${
                  isUser ? "bg-primary/5" : "border-l-[3px] border-primary bg-muted/60"
                }`}
              >
                <div className="mb-1 flex items-center gap-2 text-[11px]">
                  <span className={isUser ? "text-muted-foreground" : "font-medium text-primary"}>
                    {ROLE_LABELS[role] ?? role}
                  </span>
                  <span className="text-muted-foreground">
                    {formatUserDateTime(item.createdAt)}
                  </span>
                  {item.modelCode ? (
                    <span className="truncate text-muted-foreground">{item.modelCode}</span>
                  ) : null}
                </div>
                {isUser ? (
                  <p className="text-[13px] leading-[1.65] break-words whitespace-pre-wrap">
                    {content}
                  </p>
                ) : (
                  <MarkdownContent content={content} className="text-[13px] leading-[1.65]" />
                )}
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}

export function MemoryAtomicView({
  items,
  canEdit,
  onEdit,
  ...selection
}: {
  items: MemoryL1Item[];
  canEdit: boolean;
  onEdit: (item: MemoryL1Item) => void;
} & MemorySelectable) {
  return (
    <div className="space-y-2.5">
      {items.map((item) => (
        <div key={item.id} className="flex items-start gap-2 border bg-card p-3">
          <MemorySelection {...selection} id={item.id} label={`选择第 ${item.id} 条原子记忆`} />
          <div className="min-w-0 flex-1 space-y-2">
            <div className="flex flex-wrap items-center gap-1.5">
              <Badge variant="secondary">{MEMORY_TYPE_LABELS[item.type] ?? item.type}</Badge>
              <Badge variant="outline">{MEMORY_SCOPE_LABELS[item.scope] ?? item.scope}</Badge>
              {item.priority > 0 ? <Badge variant="outline">P{item.priority}</Badge> : null}
              {item.version > 1 ? <Badge variant="outline">v{item.version}</Badge> : null}
              {item.status && item.status !== "ACTIVE" ? (
                <Badge variant="outline">{item.status}</Badge>
              ) : null}
              <span className="ms-auto text-[11px] text-muted-foreground">
                {formatUserDateTime(item.createdAt)}
              </span>
              {canEdit ? (
                <Button type="button" variant="ghost" size="xs" onClick={() => onEdit(item)}>
                  <Pencil />
                  编辑
                </Button>
              ) : null}
            </div>
            <MarkdownContent
              content={memoryItemContent(item)}
              className="text-[13px] leading-relaxed"
            />
          </div>
        </div>
      ))}
    </div>
  );
}

function MemorySceneBody({
  assetName,
  team,
  path,
}: {
  assetName: string;
  team: string;
  path: string;
}) {
  const query = useMemorySceneBody(assetName, team, path, true);
  if (query.isLoading) {
    return (
      <div className="flex items-center gap-2 border-t bg-muted/30 px-3 py-2.5 text-xs text-muted-foreground">
        <Loader2 className="size-3.5 animate-spin" />
        加载场景内容…
      </div>
    );
  }
  if (query.isError) {
    return (
      <div className="flex items-center justify-between gap-2 border-t bg-muted/30 px-3 py-2.5 text-xs text-muted-foreground">
        <span>场景内容加载失败。</span>
        <Button type="button" variant="ghost" size="xs" onClick={() => query.refetch()}>
          <RotateCw />
          重试
        </Button>
      </div>
    );
  }
  const content = stripScenarioMeta(query.data ?? "");
  return (
    <div className="border-t bg-muted/30 px-3 py-2.5">
      {content ? (
        <MarkdownContent content={content} className="text-[13px] leading-relaxed" />
      ) : (
        <p className="text-[13px] leading-relaxed break-words whitespace-pre-wrap">
          场景内容为空。
        </p>
      )}
    </div>
  );
}

function MemorySceneRow({
  assetName,
  team,
  item,
  canEdit,
  onEdit,
}: {
  assetName: string;
  team: string;
  item: MemoryL2Item;
  canEdit: boolean;
  onEdit: (item: MemoryL2Item) => void;
}) {
  const [open, setOpen] = useState(false);
  return (
    <div className="border bg-card">
      <div className="flex items-center gap-2 p-2.5">
        <Collapsible open={open} onOpenChange={setOpen} className="min-w-0 flex-1">
          <CollapsibleTrigger asChild>
            <button
              type="button"
              className="flex w-full min-w-0 items-center gap-2 text-left outline-none focus-visible:ring-2 focus-visible:ring-ring/50"
            >
              <Badge variant="secondary" className="shrink-0">
                场景
              </Badge>
              <span className="min-w-0 flex-1">
                <span className="block truncate text-[13px] font-medium">
                  {item.summary || item.path}
                </span>
                <span className="block truncate font-mono text-[11px] text-muted-foreground">
                  {item.path}
                </span>
              </span>
              <span className="hidden shrink-0 text-[11px] text-muted-foreground sm:block">
                {formatUserDateTime(item.updatedAt)}
              </span>
              <ChevronDown
                className={`size-4 shrink-0 text-muted-foreground transition-transform ${
                  open ? "rotate-180" : ""
                }`}
              />
            </button>
          </CollapsibleTrigger>
          <CollapsibleContent>
            {open ? <MemorySceneBody assetName={assetName} team={team} path={item.path} /> : null}
          </CollapsibleContent>
        </Collapsible>
        {canEdit ? (
          <Button type="button" variant="ghost" size="xs" onClick={() => onEdit(item)}>
            <Pencil />
            编辑
          </Button>
        ) : null}
      </div>
    </div>
  );
}

export function MemorySceneView({
  assetName,
  team,
  items,
  canEdit,
  onEdit,
}: {
  assetName: string;
  team: string;
  items: MemoryL2Item[];
  canEdit: boolean;
  onEdit: (item: MemoryL2Item) => void;
}) {
  return (
    <div className="space-y-2.5">
      {items.map((item) => (
        <MemorySceneRow
          key={item.id}
          assetName={assetName}
          team={team}
          item={item}
          canEdit={canEdit}
          onEdit={onEdit}
        />
      ))}
    </div>
  );
}

export function MemoryProfileView({
  content,
  updatedAt,
  canEdit,
  onEdit,
}: {
  content: string;
  updatedAt?: string;
  canEdit: boolean;
  onEdit: () => void;
}) {
  return (
    <div className="border bg-card">
      <div className="flex items-center gap-2 border-b px-3 py-2">
        <Badge variant="secondary">核心画像</Badge>
        <span className="text-[11px] text-muted-foreground">
          更新于 {formatUserDateTime(updatedAt)}
        </span>
        {canEdit ? (
          <Button type="button" variant="ghost" size="xs" className="ms-auto" onClick={onEdit}>
            <Pencil />
            编辑
          </Button>
        ) : null}
      </div>
      <div className="p-4">
        {content ? (
          <MarkdownContent content={stripScenarioMeta(content)} />
        ) : (
          <p className="text-sm text-muted-foreground">暂无核心画像内容。</p>
        )}
      </div>
    </div>
  );
}

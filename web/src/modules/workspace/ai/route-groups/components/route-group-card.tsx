import { Layers, Pencil, Trash2 } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";

import { ProviderBrandIcon } from "../../providers/components/provider-brand-icon";
import type { AiModelItem, AiProviderItem, AiRouteGroupItem } from "../../providers/lib/api";
import { modelDisplayName } from "../../providers/lib/model";
import { routeModeLabel } from "../../routing/lib/model";
import { affinityLabel } from "../lib/model";

type RouteGroupCardProps = {
  group: AiRouteGroupItem;
  models: AiModelItem[];
  providers: AiProviderItem[];
  isDefault: boolean;
  usedByMode?: string;
  canUpdate: boolean;
  canDelete: boolean;
  toggling: boolean;
  onEdit: () => void;
  onToggle: () => void;
  onDelete: () => void;
};

export function RouteGroupCard({
  group,
  models,
  providers,
  isDefault,
  usedByMode,
  canUpdate,
  canDelete,
  toggling,
  onEdit,
  onToggle,
  onDelete,
}: RouteGroupCardProps) {
  const enabled = group.spec.enabled !== false;
  const memberCount = group.spec.members?.length ?? 0;
  const memberModels = (group.spec.members ?? [])
    .map((member) => models.find((model) => model.metadata.name === member.model))
    .filter((model): model is AiModelItem => Boolean(model));

  return (
    <article
      className={`flex flex-col rounded-2xl border bg-card/40 p-4 transition-colors hover:bg-accent/10 ${
        enabled ? "" : "opacity-70"
      }`}
    >
      <div className="flex items-start gap-3">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-background ring-1 ring-border/60">
          <Layers className="size-5 text-muted-foreground" />
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-1.5">
            <h3 className="truncate text-sm font-semibold">
              {group.spec.displayName || group.metadata.name}
            </h3>
            {isDefault ? <Badge variant="secondary">默认</Badge> : null}
            {usedByMode ? <Badge variant="outline">{usedByMode}模式</Badge> : null}
            {!enabled ? <Badge variant="outline">已停用</Badge> : null}
          </div>
          <p className="truncate font-mono text-[10px] text-muted-foreground">
            {group.metadata.name}
          </p>
        </div>
        <Switch
          checked={enabled}
          disabled={!canUpdate || toggling}
          onCheckedChange={onToggle}
          aria-label={enabled ? "停用路由组" : "启用路由组"}
        />
      </div>

      <p className="mt-3 line-clamp-2 min-h-8 text-xs leading-4 text-muted-foreground">
        {group.spec.description || "未填写备注。"}
      </p>

      <div className="mt-2 flex flex-wrap items-center gap-1.5">
        <Badge variant="outline">{routeModeLabel(group.spec.routeMode)}</Badge>
        <Badge variant="outline">{affinityLabel(group.spec.affinity)}</Badge>
        <span className="text-[11px] text-muted-foreground">{memberCount} 个模型成员</span>
      </div>

      <div className="mt-3 flex flex-wrap gap-1.5">
        {memberModels.slice(0, 4).map((model) => {
          const provider = providers.find((item) => item.metadata.name === model.spec.provider);
          return (
            <span
              key={model.metadata.name}
              className="inline-flex max-w-full items-center gap-1 rounded-full border border-border/60 bg-muted/30 px-2 py-0.5 text-[10px] text-muted-foreground"
            >
              <ProviderBrandIcon provider={provider} className="size-3" />
              <span className="truncate">{modelDisplayName(model)}</span>
            </span>
          );
        })}
        {memberCount > 4 ? (
          <span className="inline-flex items-center rounded-full border border-border/60 bg-muted/30 px-2 py-0.5 text-[10px] text-muted-foreground">
            另外 {memberCount - 4} 个
          </span>
        ) : null}
        {memberCount === 0 ? (
          <span className="text-[11px] text-muted-foreground">还没有模型成员。</span>
        ) : null}
      </div>

      {canUpdate || canDelete ? (
        <div className="mt-4 flex items-center gap-1 border-t pt-3">
          {canUpdate ? (
            <Button type="button" size="sm" variant="ghost" onClick={onEdit}>
              <Pencil />
              编辑
            </Button>
          ) : null}
          {canDelete ? (
            <Button
              type="button"
              size="sm"
              variant="ghost"
              className="text-destructive hover:text-destructive"
              onClick={onDelete}
              disabled={isDefault}
              title={isDefault ? "默认路由组不能删除，请先在设置里更换默认组。" : undefined}
            >
              <Trash2 />
              删除
            </Button>
          ) : null}
        </div>
      ) : null}
    </article>
  );
}

import { ChevronDown, ChevronUp, Copy, Layers, Pencil, Trash2 } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";

import { ProviderBrandIcon } from "../../providers/components/provider-brand-icon";
import type {
  AiModelItem,
  AiProviderAccountItem,
  AiProviderItem,
  AiRouteGroupItem,
} from "../../providers/lib/api";
import { modelDisplayName } from "../../providers/lib/model";
import { routeModeLabel } from "../../routing/lib/model";
import { affinityLabel, effectiveParentMode, parentModeLabel, routeGroupRules } from "../lib/model";

type RouteGroupCardProps = {
  group: AiRouteGroupItem;
  models: AiModelItem[];
  providers: AiProviderItem[];
  accounts: AiProviderAccountItem[];
  isDefault: boolean;
  usedByMode?: string;
  canCreate: boolean;
  canUpdate: boolean;
  canDelete: boolean;
  canArrange: boolean;
  canMoveUp: boolean;
  canMoveDown: boolean;
  toggling: boolean;
  onEdit: () => void;
  onToggle: () => void;
  onDelete: () => void;
  onCopy: () => void;
  onMoveUp: () => void;
  onMoveDown: () => void;
};

export function RouteGroupCard({
  group,
  models,
  providers,
  accounts,
  isDefault,
  usedByMode,
  canCreate,
  canUpdate,
  canDelete,
  canArrange,
  canMoveUp,
  canMoveDown,
  toggling,
  onEdit,
  onToggle,
  onDelete,
  onCopy,
  onMoveUp,
  onMoveDown,
}: RouteGroupCardProps) {
  const enabled = group.spec.enabled !== false;
  const memberCount = group.spec.members?.length ?? 0;
  const ruleCount = routeGroupRules(group).length;
  const parentMode = effectiveParentMode(group);
  const qualityThreshold = group.spec.qualityThreshold;
  const accountNames = (group.spec.accounts ?? []).map(
    (name) => accounts.find((account) => account.metadata.name === name)?.spec.displayName || name,
  );
  const memberModels = (group.spec.members ?? [])
    .map((member) => models.find((model) => model.metadata.name === member.model))
    .filter((model): model is AiModelItem => Boolean(model));

  return (
    <article
      className={`flex flex-col border bg-card/40 p-4 transition-colors hover:bg-accent/10 ${
        enabled ? "" : "opacity-70"
      }`}
    >
      <div className="flex items-start gap-3">
        <span className="flex size-10 shrink-0 items-center justify-center bg-background ring-1 ring-border/60">
          <Layers className="size-5 text-muted-foreground" />
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-1.5">
            <h3 className="truncate text-sm font-semibold">
              {group.spec.displayName || group.metadata.name}
            </h3>
            {isDefault ? <Badge variant="secondary">默认</Badge> : null}
            <Badge variant="outline">{parentModeLabel(parentMode)}</Badge>
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
        {qualityThreshold == null ? null : (
          <Badge variant="outline">质量阈值 {Number(qualityThreshold).toFixed(2)}</Badge>
        )}
        <Badge
          variant="outline"
          title={accountNames.length > 0 ? accountNames.join("、") : "不限制账号"}
        >
          {accountNames.length > 0 ? `账号 ${accountNames.length}` : "账号不限"}
        </Badge>
        <Badge variant="outline">规则 {ruleCount}</Badge>
        {group.spec.sink ? <Badge variant="outline">Sink</Badge> : null}
        <span className="text-[11px] text-muted-foreground">{memberCount} 个模型成员</span>
      </div>

      <div className="mt-3 flex flex-wrap gap-1.5">
        {memberModels.slice(0, 4).map((model) => {
          const provider = providers.find((item) => item.metadata.name === model.spec.provider);
          return (
            <span
              key={model.metadata.name}
              className="inline-flex max-w-full items-center gap-1 border border-border/60 bg-muted/30 px-2 py-0.5 text-[10px] text-muted-foreground"
            >
              <ProviderBrandIcon provider={provider} className="size-3" />
              <span className="truncate">{modelDisplayName(model)}</span>
            </span>
          );
        })}
        {memberCount > 4 ? (
          <span className="inline-flex items-center border border-border/60 bg-muted/30 px-2 py-0.5 text-[10px] text-muted-foreground">
            另外 {memberCount - 4} 个
          </span>
        ) : null}
        {memberCount === 0 ? (
          <span className="text-[11px] text-muted-foreground">还没有模型成员。</span>
        ) : null}
      </div>

      {canCreate || canUpdate || canDelete || canArrange ? (
        <div className="mt-4 flex flex-wrap items-center gap-1 border-t pt-3">
          {canCreate ? (
            <Button type="button" size="sm" variant="ghost" onClick={onCopy}>
              <Copy />
              复制
            </Button>
          ) : null}
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
          <div className="ms-auto flex items-center gap-0.5">
            <Button
              type="button"
              size="icon-xs"
              variant="ghost"
              onClick={onMoveUp}
              disabled={!canArrange || !canMoveUp}
              aria-label="上移路由组"
              title="上移"
            >
              <ChevronUp />
            </Button>
            <Button
              type="button"
              size="icon-xs"
              variant="ghost"
              onClick={onMoveDown}
              disabled={!canArrange || !canMoveDown}
              aria-label="下移路由组"
              title="下移"
            >
              <ChevronDown />
            </Button>
          </div>
        </div>
      ) : null}
    </article>
  );
}

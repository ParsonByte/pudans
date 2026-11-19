import type { ReactNode } from "react";

import { Loader2, Settings2 } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { ConsoleSection } from "@/modules/workspace/console/components/console-page";

import type { AiRouteGroupItem, AiRouteGroupTier, AiSettingItem } from "../../providers/lib/api";
import { ROUTE_GROUP_TIER_OPTIONS, routeGroupTierLabel } from "../lib/model";

type RouteSettingsProps = {
  setting: AiSettingItem | undefined;
  routeGroups: AiRouteGroupItem[];
  loading: boolean;
  error: boolean;
  canUpdate: boolean;
  saving: boolean;
  onChange: (patch: {
    defaultRouteGroup?: string | null;
    classifierEnabled?: boolean;
    classifierFallbackTier?: AiRouteGroupTier;
  }) => void;
};

export function RouteSettings({
  setting,
  routeGroups,
  loading,
  error,
  canUpdate,
  saving,
  onChange,
}: RouteSettingsProps) {
  const configuredDefault = setting?.spec.defaultRouteGroup;
  const defaultRouteGroup =
    configuredDefault && routeGroups.some((group) => group.metadata.name === configuredDefault)
      ? configuredDefault
      : "none";
  const classifierEnabled = setting?.spec.routing?.classifierEnabled !== false;
  const fallbackTier = setting?.spec.routing?.classifierFallbackTier ?? "HIGH";
  const disabled = !canUpdate || saving || !setting;

  return (
    <ConsoleSection>
      <div className="flex flex-wrap items-start justify-between gap-3 border-b px-5 py-4">
        <div className="flex items-start gap-3">
          <span className="flex size-8 items-center justify-center rounded-lg bg-muted">
            <Settings2 className="size-4 text-muted-foreground" />
          </span>
          <div className="space-y-1">
            <h2 className="text-sm font-semibold">设置</h2>
            <p className="text-xs text-muted-foreground">影响所有路由组和智能分级的全局选项。</p>
          </div>
        </div>
        {saving ? (
          <Badge variant="outline">
            <Loader2 className="animate-spin" />
            保存中
          </Badge>
        ) : null}
      </div>

      {loading ? (
        <div className="space-y-3 px-5 py-5">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
        </div>
      ) : error || !setting ? (
        <p className="px-5 py-5 text-xs text-muted-foreground">
          无法读取 AI 设置，当前用户可能没有设置读取权限。
        </p>
      ) : (
        <div className="divide-y">
          <SettingRow
            label="默认路由组"
            hint="Agent 未指定路由组时使用这一组。"
            control={
              <Select
                value={defaultRouteGroup}
                onValueChange={(value) =>
                  onChange({ defaultRouteGroup: value === "none" ? null : value })
                }
                disabled={disabled || routeGroups.length === 0}
              >
                <SelectTrigger className="w-56">
                  <SelectValue placeholder="未指定" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="none">未指定</SelectItem>
                  {routeGroups.map((group) => (
                    <SelectItem key={group.metadata.name} value={group.metadata.name}>
                      {group.spec.displayName || group.metadata.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            }
          />
          <SettingRow
            label="智能分级"
            hint="开启后，智能路由会按请求复杂度选择模型档位。"
            control={
              <Switch
                checked={classifierEnabled}
                disabled={disabled}
                onCheckedChange={(checked) => onChange({ classifierEnabled: checked })}
                aria-label="智能分级"
              />
            }
          />
          <SettingRow
            label="分级兜底等级"
            hint="分类器不可用时使用的兜底档位。"
            control={
              <Select
                value={fallbackTier}
                onValueChange={(value) =>
                  onChange({ classifierFallbackTier: value as AiRouteGroupTier })
                }
                disabled={disabled}
              >
                <SelectTrigger className="w-40">
                  <SelectValue placeholder={routeGroupTierLabel(fallbackTier)} />
                </SelectTrigger>
                <SelectContent>
                  {ROUTE_GROUP_TIER_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            }
          />
        </div>
      )}
    </ConsoleSection>
  );
}

function SettingRow({ label, hint, control }: { label: string; hint: string; control: ReactNode }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 px-5 py-4">
      <div className="min-w-0 space-y-1">
        <p className="text-sm font-medium">{label}</p>
        <p className="text-xs text-muted-foreground">{hint}</p>
      </div>
      {control}
    </div>
  );
}

import { Loader2, Plus, Wand2 } from "lucide-react";
import Link from "next/link";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { ConsoleEmpty, ConsoleSection } from "@/modules/workspace/console/components/console-page";

import { ProviderBrandIcon } from "../../providers/components/provider-brand-icon";
import type { AiProviderItem } from "../../providers/lib/api";
import { modelDisplayName } from "../../providers/lib/model";
import type { RouteTemplate } from "../lib/model";

type RouteTemplateGridProps = {
  templates: RouteTemplate[];
  providers: AiProviderItem[];
  loading: boolean;
  canCreate: boolean;
  providersPath: string | undefined;
  pendingTemplateId: string | undefined;
  onAdd: (template: RouteTemplate) => void;
  onCustomize: (template: RouteTemplate) => void;
};

export function RouteTemplateGrid({
  templates,
  providers,
  loading,
  canCreate,
  providersPath,
  pendingTemplateId,
  onAdd,
  onCustomize,
}: RouteTemplateGridProps) {
  const hasModels = templates.some((template) => template.models.length > 0);

  return (
    <ConsoleSection>
      <div className="flex flex-wrap items-start justify-between gap-3 border-b px-5 py-4">
        <div className="space-y-1">
          <h2 className="text-sm font-semibold">从模板新建（可选）</h2>
          <p className="text-xs text-muted-foreground">
            用当前模型快速创建额外路由组；聊天模式仍使用上方已绑定的组。
          </p>
        </div>
        <Badge variant="outline">
          <Wand2 />
          基于当前模型
        </Badge>
      </div>

      {loading ? (
        <div className="grid gap-3 px-5 py-5 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-5">
          {Array.from({ length: 6 }, (_, index) => (
            <Skeleton key={index} className="h-52" />
          ))}
        </div>
      ) : !hasModels ? (
        <ConsoleEmpty>
          <span className="space-y-2">
            <span className="block">还没有可用模型，模板暂时无法生成。</span>
            {providersPath ? (
              <Link
                href={providersPath}
                className="text-sm font-medium text-primary hover:underline"
              >
                去供应商添加账号和模型
              </Link>
            ) : null}
          </span>
        </ConsoleEmpty>
      ) : (
        <div className="grid gap-3 px-5 py-5 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-5">
          {templates.map((template) => (
            <TemplateCard
              key={template.id}
              template={template}
              providers={providers}
              canCreate={canCreate}
              pending={pendingTemplateId === template.id}
              onAdd={() => onAdd(template)}
              onCustomize={() => onCustomize(template)}
            />
          ))}
        </div>
      )}
    </ConsoleSection>
  );
}

function TemplateCard({
  template,
  providers,
  canCreate,
  pending,
  onAdd,
  onCustomize,
}: {
  template: RouteTemplate;
  providers: AiProviderItem[];
  canCreate: boolean;
  pending: boolean;
  onAdd: () => void;
  onCustomize: () => void;
}) {
  const firstModel = template.models[0];
  const provider = firstModel
    ? providers.find((item) => item.metadata.name === firstModel.spec.provider)
    : undefined;
  const models = template.models.slice(0, 2).map((model) => modelDisplayName(model));

  return (
    <article
      className={`flex min-h-52 flex-col border p-4 transition-colors ${
        template.highlighted
          ? "border-primary/40 bg-primary/5"
          : template.available
            ? "bg-card/40"
            : "bg-card/20 opacity-65"
      }`}
    >
      <div className="flex items-start justify-between gap-2">
        <h3 className="text-sm font-semibold">{template.title}</h3>
        {provider ? (
          <span className="flex size-7 shrink-0 items-center justify-center bg-background ring-1 ring-border/60">
            <ProviderBrandIcon provider={provider} className="size-4" />
          </span>
        ) : null}
      </div>
      <p className="mt-2 text-xs leading-5 text-muted-foreground">{template.description}</p>
      {models.length > 0 ? (
        <p className="mt-1.5 truncate font-mono text-[10px] text-muted-foreground">
          {models.join(" · ")}
          {template.models.length > 2 ? ` 等 ${template.models.length} 个` : ""}
        </p>
      ) : null}

      <p className="mt-auto pt-3 text-[11px] leading-4 text-muted-foreground">
        {template.requirement}
      </p>

      {canCreate && template.available ? (
        <div className="mt-3 flex items-center gap-1 border-t pt-3">
          <Button type="button" size="sm" onClick={onAdd} disabled={pending}>
            {pending ? <Loader2 className="animate-spin" /> : <Plus />}
            添加
          </Button>
          <Button type="button" size="sm" variant="ghost" onClick={onCustomize} disabled={pending}>
            先编辑
          </Button>
        </div>
      ) : null}
    </article>
  );
}

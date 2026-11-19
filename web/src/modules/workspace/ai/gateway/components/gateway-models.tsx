import { Link } from "@tanstack/react-router";
import { Boxes } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { ConsoleEmpty, ConsoleSection } from "@/modules/workspace/console/components/console-page";

import { ProviderBrandIcon } from "../../providers/components/provider-brand-icon";
import type { AiModelItem, AiProviderItem } from "../../providers/lib/api";
import { MODEL_TYPE_OPTIONS, modelDisplayName, optionLabel } from "../../providers/lib/model";
import { formatTokenCount } from "../../routing/lib/model";
import { CopyButton } from "./copy-field";

type GatewayModelsProps = {
  models: AiModelItem[];
  providers: AiProviderItem[];
  loading: boolean;
  providersPath: string | undefined;
};

export function GatewayModels({ models, providers, loading, providersPath }: GatewayModelsProps) {
  const ids = models.map((model) => `${model.spec.provider}/${model.spec.modelCode}`);

  return (
    <ConsoleSection>
      <div className="flex flex-wrap items-start justify-between gap-3 border-b px-5 py-4">
        <div className="space-y-1">
          <h2 className="text-sm font-semibold">
            模型 <span className="text-muted-foreground">{models.length}</span>
          </h2>
          <p className="text-xs text-muted-foreground">
            网关可以路由到的模型；ID 格式为供应商/模型。
          </p>
        </div>
        {models.length > 0 ? (
          <CopyButton value={ids.join("\n")} label="全部模型 ID" className="size-8" />
        ) : null}
      </div>

      {loading ? (
        <div className="space-y-2 px-5 py-4">
          <Skeleton className="h-14 w-full" />
          <Skeleton className="h-14 w-full" />
        </div>
      ) : models.length === 0 ? (
        <ConsoleEmpty>
          <span className="space-y-2">
            <span className="block">还没有可用模型。</span>
            {providersPath ? (
              <Link to={providersPath} className="text-sm font-medium text-primary hover:underline">
                去供应商添加账号和模型
              </Link>
            ) : null}
          </span>
        </ConsoleEmpty>
      ) : (
        <div className="divide-y">
          {models.map((model) => {
            const provider = providers.find((item) => item.metadata.name === model.spec.provider);
            const id = `${model.spec.provider}/${model.spec.modelCode}`;
            return (
              <div
                key={model.metadata.name}
                className="flex items-center gap-3 px-5 py-3 transition-colors hover:bg-muted/20"
              >
                <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-background ring-1 ring-border/60">
                  <ProviderBrandIcon provider={provider} className="size-5" />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="truncate font-mono text-sm">{id}</p>
                  <p className="truncate text-[11px] text-muted-foreground">
                    {modelDisplayName(model)} · {provider?.spec.displayName ?? model.spec.provider}
                  </p>
                </div>
                <div className="flex shrink-0 items-center gap-1.5">
                  <Badge variant="outline">
                    {optionLabel(MODEL_TYPE_OPTIONS, model.spec.modelType)}
                  </Badge>
                  <Badge variant="secondary">
                    {model.spec.contextWindow
                      ? formatTokenCount(model.spec.contextWindow)
                      : "未设上下文"}
                  </Badge>
                </div>
                <CopyButton value={id} label="模型 ID" />
              </div>
            );
          })}
        </div>
      )}
      {models.length === 0 && !loading ? (
        <div className="flex items-center gap-2 border-t px-5 py-3 text-[11px] text-muted-foreground">
          <Boxes className="size-3.5" />
          模型需要在供应商里导入或手动添加。
        </div>
      ) : null}
    </ConsoleSection>
  );
}

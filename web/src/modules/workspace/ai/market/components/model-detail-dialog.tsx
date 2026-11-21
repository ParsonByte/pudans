import type { ReactNode } from "react";

import { useQuery } from "@tanstack/react-query";

import { MarkdownContent } from "@/components/content/markdown";
import { DetailDialog } from "@/components/detail-dialog";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { billingRatios, billingTierMultipliers } from "@/modules/workspace/ai/plans/lib/model";
import { ConsoleState } from "@/modules/workspace/console/components/console-state";

import { ProviderBrandIcon } from "../../providers/components/provider-brand-icon";
import { fetchAiSetting } from "../../providers/lib/api";
import { formatNumber, modelDisplayName } from "../../providers/lib/model";
import {
  CAPABILITY_LABELS,
  MODALITY_LABELS,
  type ModelInfo,
  modelDescription,
  modelTierLabel,
  modelTypeLabel,
} from "../lib/model";

type ModelDetailDialogProps = {
  info: ModelInfo | null;
  onClose: () => void;
};

export function ModelDetailDialog({ info, onClose }: ModelDetailDialogProps) {
  return (
    <DetailDialog
      open={info !== null}
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
      title={info ? modelDisplayName(info.model) : "模型详情"}
      description={info?.model.spec.modelCode}
    >
      {info ? <ModelDetailBody info={info} /> : null}
    </DetailDialog>
  );
}

function ModelDetailBody({ info }: { info: ModelInfo }) {
  const { model, provider } = info;
  const spec = model.spec;
  const description = modelDescription(spec);
  const facts: { label: string; value: ReactNode; mono?: boolean }[] = [
    {
      label: "供应商",
      value: (
        <span className="flex min-w-0 items-center gap-1.5">
          <ProviderBrandIcon provider={provider} className="size-3.5" />
          <span className="truncate">{provider?.spec.displayName || spec.provider}</span>
        </span>
      ),
    },
    { label: "资源名称", value: model.metadata.name, mono: true },
    { label: "模型类别", value: modelTypeLabel(spec.modelType) },
    { label: "档位", value: modelTierLabel(spec.tier) },
    {
      label: "状态",
      value: (
        <Badge variant={spec.enabled === false ? "outline" : "secondary"}>
          {spec.enabled === false ? "已停用" : "已启用"}
        </Badge>
      ),
    },
    {
      label: "上下文窗口",
      value: spec.contextWindow ? formatNumber(spec.contextWindow) : "未设置",
    },
    {
      label: "最大输出",
      value: spec.maxOutputTokens ? formatNumber(spec.maxOutputTokens) : "未设置",
    },
  ];

  if (spec.embeddingDim != null) {
    facts.push({ label: "嵌入维度", value: formatNumber(spec.embeddingDim) });
  }
  if (spec.maxBatchSize != null) {
    facts.push({ label: "最大批处理", value: formatNumber(spec.maxBatchSize) });
  }
  if (spec.maxInputImages != null) {
    facts.push({ label: "最大输入图片", value: formatNumber(spec.maxInputImages) });
  }
  if (spec.maxInputSeconds != null) {
    facts.push({ label: "最大输入时长", value: `${formatNumber(spec.maxInputSeconds)} 秒` });
  }
  if (spec.maxOutputItems != null) {
    facts.push({ label: "最大输出数量", value: formatNumber(spec.maxOutputItems) });
  }

  return (
    <div className="space-y-6">
      {description ? (
        <section className="space-y-2">
          <h3 className="text-sm font-semibold">模型说明</h3>
          <MarkdownContent content={description} />
        </section>
      ) : null}

      <section className="space-y-2">
        <h3 className="text-sm font-semibold">基本信息</h3>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {facts.map((fact) => (
            <ModelFact key={fact.label} {...fact} />
          ))}
        </div>
      </section>

      <section className="space-y-2">
        <h3 className="text-sm font-semibold">模型能力</h3>
        {info.capabilities.length > 0 ? (
          <div className="flex flex-wrap gap-1.5">
            {info.capabilities.map((capability) => (
              <Badge key={capability} variant="outline">
                {CAPABILITY_LABELS[capability]}
              </Badge>
            ))}
          </div>
        ) : (
          <p className="text-xs text-muted-foreground">该模型暂未标注能力。</p>
        )}
      </section>

      <section className="space-y-2">
        <h3 className="text-sm font-semibold">模态</h3>
        <div className="grid gap-3 sm:grid-cols-2">
          <ModelFact
            label="输入模态"
            value={info.inputs.map((modality) => MODALITY_LABELS[modality]).join(" / ") || "—"}
          />
          <ModelFact
            label="输出模态"
            value={info.outputs.map((modality) => MODALITY_LABELS[modality]).join(" / ") || "—"}
          />
        </div>
      </section>

      <section className="space-y-2">
        <h3 className="text-sm font-semibold">计费权重</h3>
        <ModelPricing info={info} />
      </section>
    </div>
  );
}

function ModelPricing({ info }: { info: ModelInfo }) {
  const settingQuery = useQuery({
    queryKey: ["ai-settings", "ai-setting"],
    queryFn: fetchAiSetting,
    retry: false,
    staleTime: 5 * 60_000,
  });

  if (settingQuery.isError) {
    return (
      <ConsoleState
        variant="error"
        title="无法加载计费设置"
        description="请稍后重试。"
        onRetry={() => void settingQuery.refetch()}
        className="min-h-0 px-0 py-4"
      />
    );
  }

  if (settingQuery.isPending) {
    return <p className="text-xs text-muted-foreground">正在加载计费设置…</p>;
  }

  const billing = settingQuery.data?.spec.billing;
  const ratios = billingRatios(billing);
  const tier = info.model.spec.tier;
  const tierMultiplier = tier ? billingTierMultipliers(billing)[tier] : 1;
  const modelMultiplier = billing?.modelMultipliers?.[info.model.spec.modelCode] ?? 1;
  const combined = tierMultiplier * modelMultiplier;

  return (
    <div className="space-y-2">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <ModelFact label="档位系数" value={formatNumber(tierMultiplier)} />
        <ModelFact label="模型系数" value={formatNumber(modelMultiplier)} />
        <ModelFact label="综合系数" value={formatNumber(combined)} />
        <ModelFact label="输入权重" value={formatNumber(ratios.input)} />
        <ModelFact label="输出权重" value={formatNumber(ratios.output)} />
        <ModelFact label="缓存读取权重" value={formatNumber(ratios.cacheRead)} />
        <ModelFact label="缓存写入权重" value={formatNumber(ratios.cacheWrite)} />
      </div>
      <p className="text-xs text-muted-foreground">
        标准权重 × 档位系数 × 模型系数；未单独覆盖的模型系数按 1 计算。
      </p>
    </div>
  );
}

function ModelFact({
  label,
  value,
  mono = false,
}: {
  label: string;
  value: ReactNode;
  mono?: boolean;
}) {
  return (
    <div className="min-w-0 border bg-card/40 px-3 py-2">
      <p className="text-[11px] text-muted-foreground">{label}</p>
      <div className={cn("min-w-0 text-sm", mono && "font-mono text-xs break-all")}>{value}</div>
    </div>
  );
}

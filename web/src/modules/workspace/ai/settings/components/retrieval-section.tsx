import { Controller } from "react-hook-form";

import type { SettingsFormApi } from "../lib/schema";
import { NumberField, SettingsSection, SwitchField, TextField } from "./settings-fields";

type RetrievalSectionProps = {
  form: SettingsFormApi;
};

export function RetrievalSection({ form }: RetrievalSectionProps) {
  return (
    <SettingsSection
      title="检索"
      description="知识库检索的融合策略、召回上限、分数阈值与 Agent 工具化检索开关。"
    >
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        <TextField
          id="ai-setting-retrieval-strategy"
          label="检索策略"
          hint="例如 HYBRID；由检索服务解析。"
          registration={form.register("retrieval.strategy")}
          error={form.formState.errors.retrieval?.strategy?.message}
        />
        <NumberField
          id="ai-setting-retrieval-rrf-k"
          label="RRF K"
          hint="倒数排名融合的平滑常数。"
          min={1}
          registration={form.register("retrieval.rrfK")}
          error={form.formState.errors.retrieval?.rrfK?.message}
        />
        <NumberField
          id="ai-setting-retrieval-score-threshold"
          label="分数阈值"
          hint="0 到 1 之间；低于该分数的片段会被丢弃。"
          min={0}
          step="0.01"
          registration={form.register("retrieval.scoreThreshold")}
          error={form.formState.errors.retrieval?.scoreThreshold?.message}
        />
        <NumberField
          id="ai-setting-retrieval-l1-limit"
          label="一级召回上限"
          hint="融合前每一路召回的片段数量。"
          min={1}
          registration={form.register("retrieval.l1Limit")}
          error={form.formState.errors.retrieval?.l1Limit?.message}
        />
        <NumberField
          id="ai-setting-retrieval-l2-limit"
          label="二级召回上限"
          min={0}
          registration={form.register("retrieval.l2Limit")}
          error={form.formState.errors.retrieval?.l2Limit?.message}
        />
        <NumberField
          id="ai-setting-retrieval-org-limit"
          label="组织召回上限"
          min={0}
          registration={form.register("retrieval.orgLimit")}
          error={form.formState.errors.retrieval?.orgLimit?.message}
        />
      </div>

      <Controller
        control={form.control}
        name="retrieval.agenticEnabled"
        render={({ field }) => (
          <SwitchField
            id="ai-setting-retrieval-agentic"
            label="Agent 工具化检索"
            description="启用后知识库检索以工具形式交给 Agent 自主调用。"
            checked={field.value}
            onCheckedChange={field.onChange}
          />
        )}
      />
    </SettingsSection>
  );
}

import type { SettingsFormApi } from "../lib/schema";
import { NumberField, SettingsSection } from "./settings-fields";

type PromptSectionProps = {
  form: SettingsFormApi;
};

export function PromptSection({ form }: PromptSectionProps) {
  return (
    <SettingsSection
      title="提示词预算"
      description="拼装系统提示词时的 Token 与历史窗口上限，以及知识库片段的字符预算。"
    >
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <NumberField
          id="ai-setting-prompt-max-input"
          label="最大输入 Token"
          min={1}
          registration={form.register("prompt.maxInputTokens")}
          error={form.formState.errors.prompt?.maxInputTokens?.message}
        />
        <NumberField
          id="ai-setting-prompt-max-output"
          label="最大输出 Token"
          min={1}
          registration={form.register("prompt.maxOutputTokens")}
          error={form.formState.errors.prompt?.maxOutputTokens?.message}
        />
        <NumberField
          id="ai-setting-prompt-history-turns"
          label="历史窗口轮数"
          min={0}
          registration={form.register("prompt.historyWindowTurns")}
          error={form.formState.errors.prompt?.historyWindowTurns?.message}
        />
        <NumberField
          id="ai-setting-prompt-kb-budget"
          label="知识库字符预算"
          hint="注入提示词的知识库片段字符上限。"
          min={0}
          registration={form.register("prompt.knowledgeBaseCharBudget")}
          error={form.formState.errors.prompt?.knowledgeBaseCharBudget?.message}
        />
      </div>
    </SettingsSection>
  );
}

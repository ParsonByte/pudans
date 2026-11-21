import type { SettingsFormApi } from "../lib/schema";
import { NumberField, SettingsSection, TextField } from "./settings-fields";

type EmbeddingSectionProps = {
  form: SettingsFormApi;
};

export function EmbeddingSection({ form }: EmbeddingSectionProps) {
  return (
    <SettingsSection
      title="向量嵌入"
      description="记忆、知识库与检索查询分别可使用不同的嵌入模型；维度与批大小作用于整体嵌入链路。"
    >
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        <NumberField
          id="ai-setting-embedding-dimension"
          label="向量维度"
          hint="必须与嵌入模型输出维度一致。"
          min={1}
          registration={form.register("embedding.dimension")}
          error={form.formState.errors.embedding?.dimension?.message}
        />
        <NumberField
          id="ai-setting-embedding-batch-size"
          label="批处理大小"
          hint="单次嵌入请求最多处理的文本数。"
          min={1}
          registration={form.register("embedding.batchSize")}
          error={form.formState.errors.embedding?.batchSize?.message}
        />
        <TextField
          id="ai-setting-embedding-memory-model"
          label="记忆嵌入模型"
          hint="留空时使用服务端默认模型。"
          registration={form.register("embedding.memoryModel")}
          error={form.formState.errors.embedding?.memoryModel?.message}
        />
        <TextField
          id="ai-setting-embedding-knowledge-model"
          label="知识库嵌入模型"
          hint="留空时使用服务端默认模型。"
          registration={form.register("embedding.knowledgeBaseModel")}
          error={form.formState.errors.embedding?.knowledgeBaseModel?.message}
        />
        <TextField
          id="ai-setting-embedding-query-model"
          label="查询嵌入模型"
          hint="用于检索查询向量化；留空时跟随知识库模型。"
          registration={form.register("embedding.queryModel")}
          error={form.formState.errors.embedding?.queryModel?.message}
        />
      </div>
    </SettingsSection>
  );
}

import { MarkdownContent } from "@/components/content/markdown";
import { ConsoleEmpty } from "@/modules/workspace/console/components/console-page";
import { ArtifactBlock } from "@/modules/workspace/index/conversation/components/artifact-block";

import type { AiGenerationOutput } from "../lib/api";
import {
  generationOutputArtifact,
  generationOutputJson,
  generationOutputText,
  generationOutputTypeLabel,
  generationVectorSummary,
} from "../lib/model";

type GenerationOutputsProps = {
  outputs: AiGenerationOutput[];
};

export function GenerationOutputs({ outputs }: GenerationOutputsProps) {
  if (outputs.length === 0) {
    return <ConsoleEmpty>本次生成没有返回内容。</ConsoleEmpty>;
  }

  return (
    <div className="space-y-3">
      {outputs.map((output, index) => (
        <GenerationOutput key={`${output.type}-${index}`} output={output} index={index} />
      ))}
    </div>
  );
}

function GenerationOutput({ output, index }: { output: AiGenerationOutput; index: number }) {
  const text = generationOutputText(output);
  if (text !== null) {
    return (
      <section className="space-y-1">
        <p className="text-xs font-medium text-muted-foreground">文本输出 {index + 1}</p>
        {text.trim() ? (
          <div className="border bg-muted/30 p-3">
            <MarkdownContent content={text} className="text-xs leading-relaxed" />
          </div>
        ) : (
          <p className="border border-dashed px-3 py-2 text-xs text-muted-foreground">
            文本内容为空。
          </p>
        )}
      </section>
    );
  }

  const artifact = generationOutputArtifact(output, index);
  if (artifact) {
    return <ArtifactBlock artifact={artifact} />;
  }

  const json = generationOutputJson(output);
  if (json !== null) {
    return (
      <section className="space-y-1">
        <p className="text-xs font-medium text-muted-foreground">JSON 输出</p>
        <pre className="max-h-72 overflow-auto border bg-muted/30 p-3 font-mono text-xs">
          {json}
        </pre>
      </section>
    );
  }

  if (output.type?.toLowerCase() === "vector") {
    return (
      <p className="border bg-muted/30 px-3 py-2 text-xs text-muted-foreground">
        向量输出：{generationVectorSummary(output)}
      </p>
    );
  }

  return (
    <p className="border border-dashed px-3 py-2 text-xs text-muted-foreground">
      {generationOutputTypeLabel(output.type)}输出没有可展示的内容。
    </p>
  );
}

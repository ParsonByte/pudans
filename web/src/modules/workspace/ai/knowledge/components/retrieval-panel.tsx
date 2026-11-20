import { type Resolver, type UseFormRegisterReturn, useForm } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { Loader2, Search } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";

import {
  type KnowledgeBaseItem,
  resolveKnowledgeBaseApiError,
  searchKnowledgeBase,
} from "../lib/api";
import { formatScore, knowledgeBaseCode } from "../lib/model";
import { HighlightedSnippet } from "./highlighted-snippet";
import { KnowledgeErrorState } from "./knowledge-states";

const retrievalSchema = z.object({
  q: z.string().trim().min(1, "请输入检索问题。").max(2000, "问题不能超过 2000 个字符。"),
  topK: z.coerce
    .number({ error: "请输入有效数字。" })
    .int()
    .min(1, "召回数量必须大于 0。")
    .max(50, "召回数量最多 50。"),
  scoreThreshold: z.coerce
    .number({ error: "请输入有效数字。" })
    .min(0, "分数阈值不能小于 0。")
    .max(1, "分数阈值不能大于 1。"),
  rrfK: z.coerce
    .number({ error: "请输入有效数字。" })
    .int()
    .min(1, "RRF 常数必须大于 0。")
    .max(1000, "RRF 常数最多 1000。"),
  charBudget: z.coerce
    .number({ error: "请输入有效数字。" })
    .int()
    .min(1, "字符预算必须大于 0。")
    .max(100000, "字符预算最多 100000。"),
});

type RetrievalFormValues = z.infer<typeof retrievalSchema>;

type RetrievalPanelProps = {
  knowledgeBase: KnowledgeBaseItem;
};

export function RetrievalPanel({ knowledgeBase }: RetrievalPanelProps) {
  const spec = knowledgeBase.spec;
  const form = useForm<RetrievalFormValues>({
    resolver: zodResolver(retrievalSchema) as Resolver<RetrievalFormValues>,
    defaultValues: {
      q: "",
      topK: spec.topK ?? 8,
      scoreThreshold: spec.scoreThreshold ?? 0.3,
      rrfK: 60,
      charBudget: spec.charBudget ?? 4000,
    },
  });
  const searchMutation = useMutation({
    mutationFn: (values: RetrievalFormValues) =>
      searchKnowledgeBase({
        kbCode: knowledgeBaseCode(knowledgeBase.metadata.name),
        q: values.q.trim(),
        topK: values.topK,
        scoreThreshold: values.scoreThreshold,
        rrfK: values.rrfK,
        charBudget: values.charBudget,
      }),
    onError: (error) => toast.error(resolveKnowledgeBaseApiError(error)),
  });

  const submit = form.handleSubmit((values) => searchMutation.mutate(values));
  const result = searchMutation.data;
  const submittedQuery = searchMutation.variables?.q.trim() ?? "";

  return (
    <div className="space-y-4">
      <p className="text-xs text-muted-foreground">
        检索范围：当前知识库「{knowledgeBase.spec.displayName || knowledgeBase.metadata.name}」。
      </p>

      <form onSubmit={submit} className="space-y-4 rounded-lg border p-4" noValidate>
        <div className="space-y-2">
          <Label htmlFor="retrieval-query">检索问题</Label>
          <Textarea
            id="retrieval-query"
            placeholder="例如：如何重置企业账号密码？"
            rows={2}
            aria-invalid={Boolean(form.formState.errors.q)}
            {...form.register("q")}
          />
          {form.formState.errors.q ? (
            <p className="text-xs text-destructive">{form.formState.errors.q.message}</p>
          ) : null}
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <NumberField
            id="retrieval-top-k"
            label="召回数量 topK"
            hint="1 – 50"
            error={form.formState.errors.topK?.message}
            registration={form.register("topK", { valueAsNumber: true })}
            min={1}
            max={50}
          />
          <NumberField
            id="retrieval-score-threshold"
            label="分数阈值"
            hint="0 – 1"
            error={form.formState.errors.scoreThreshold?.message}
            registration={form.register("scoreThreshold", { valueAsNumber: true })}
            min={0}
            max={1}
            step={0.05}
          />
          <NumberField
            id="retrieval-rrf-k"
            label="RRF 常数"
            hint="1 – 1000"
            error={form.formState.errors.rrfK?.message}
            registration={form.register("rrfK", { valueAsNumber: true })}
            min={1}
            max={1000}
          />
          <NumberField
            id="retrieval-char-budget"
            label="字符预算"
            hint="1 – 100000"
            error={form.formState.errors.charBudget?.message}
            registration={form.register("charBudget", { valueAsNumber: true })}
            min={1}
            max={100000}
          />
        </div>

        <div className="flex justify-end">
          <Button type="submit" disabled={searchMutation.isPending}>
            {searchMutation.isPending ? <Loader2 className="animate-spin" /> : <Search />}
            开始检索
          </Button>
        </div>
      </form>

      {searchMutation.isPending ? (
        <div className="space-y-3">
          {Array.from({ length: 3 }, (_, index) => (
            <Skeleton key={index} className="h-24 rounded-lg" />
          ))}
        </div>
      ) : searchMutation.isError ? (
        <KnowledgeErrorState
          title="检索失败"
          message={resolveKnowledgeBaseApiError(searchMutation.error)}
          onRetry={() => searchMutation.mutate(searchMutation.variables ?? form.getValues())}
          className="border-dashed bg-transparent"
        />
      ) : result ? (
        result.citations.length === 0 ? (
          <div className="rounded-lg border border-dashed p-6 text-center text-xs text-muted-foreground">
            没有命中任何片段。可尝试降低分数阈值、提高召回数量，或更换检索问题。
          </div>
        ) : (
          <div className="space-y-3">
            <p className="text-xs text-muted-foreground">
              命中 {result.citations.length} 个片段，分数越高表示与问题越相关。
            </p>
            {result.citations.map((citation) => (
              <div
                key={`${citation.index}-${citation.docId}`}
                className="space-y-2 rounded-lg border p-4"
              >
                <div className="flex flex-wrap items-center gap-2">
                  <Badge>引用 {citation.index}</Badge>
                  <Badge variant="secondary">分数 {formatScore(citation.score)}</Badge>
                  <span className="min-w-0 text-xs text-muted-foreground">
                    《{citation.fileName}》{citation.pageNo ? ` 第 ${citation.pageNo} 页` : ""}
                    {citation.headingPath ? ` · ${citation.headingPath}` : ""}
                  </span>
                </div>
                <HighlightedSnippet text={citation.snippet} query={submittedQuery} />
              </div>
            ))}
          </div>
        )
      ) : (
        <div className="rounded-lg border border-dashed p-6 text-center text-xs text-muted-foreground">
          输入问题后执行检索测试，查看命中片段、分数与引用来源。
        </div>
      )}
    </div>
  );
}

type NumberFieldProps = {
  id: string;
  label: string;
  hint: string;
  error?: string;
  registration: UseFormRegisterReturn;
  min: number;
  max: number;
  step?: number;
};

function NumberField({ id, label, hint, error, registration, min, max, step }: NumberFieldProps) {
  return (
    <div className="space-y-2">
      <Label htmlFor={id}>{label}</Label>
      <Input
        id={id}
        type="number"
        min={min}
        max={max}
        step={step}
        aria-invalid={Boolean(error)}
        {...registration}
      />
      {error ? (
        <p className="text-xs text-destructive">{error}</p>
      ) : (
        <p className="text-xs text-muted-foreground">{hint}</p>
      )}
    </div>
  );
}

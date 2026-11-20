import { useState } from "react";

import { ChevronDown, CircleAlert, Loader2, Wrench } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import {
  CodeBlock,
  CodeBlockActions,
  CodeBlockContent,
  CodeBlockCopyButton,
  CodeBlockHeader,
  CodeBlockTitle,
} from "@/components/ui/code-block";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { cn } from "@/lib/utils";

import type { ChatToolCall, ChatToolResult } from "../../lib/chat-api";

type ToolCallsBlockProps = {
  calls: ChatToolCall[];
  results: ChatToolResult[];
  streaming?: boolean;
};

export function ToolCallsBlock({ calls, results, streaming = false }: ToolCallsBlockProps) {
  const resultById = new Map(results.map((result) => [result.id, result]));
  const callIds = new Set(calls.map((call) => call.id));
  const orphanResults = results.filter((result) => !callIds.has(result.id));
  if (calls.length === 0 && orphanResults.length === 0) {
    return null;
  }
  return (
    <div className="w-full space-y-1.5">
      {calls.map((call) => (
        <ToolCallItem
          key={call.id}
          call={call}
          result={resultById.get(call.id)}
          streaming={streaming}
        />
      ))}
      {orphanResults.map((result) => (
        <ToolCallItem key={result.id} result={result} streaming={false} />
      ))}
    </div>
  );
}

function ToolCallItem({
  call,
  result,
  streaming,
}: {
  call?: ChatToolCall;
  result?: ChatToolResult;
  streaming: boolean;
}) {
  const [open, setOpen] = useState(false);
  const name = call?.name ?? result?.name ?? "工具调用";
  const argumentsText = call ? formatJson(call.arguments) : "";

  return (
    <Collapsible open={open} onOpenChange={setOpen}>
      <div className="border bg-muted/30 text-xs">
        <CollapsibleTrigger asChild>
          <button
            type="button"
            className="flex w-full items-center gap-2 px-2.5 py-1.5 text-start outline-none focus-visible:ring-2 focus-visible:ring-ring/50"
          >
            <Wrench className="size-3.5 shrink-0 text-muted-foreground" />
            <span className="min-w-0 truncate font-mono font-medium">{name}</span>
            {call?.server ? (
              <span className="shrink-0 text-muted-foreground">@{call.server}</span>
            ) : null}
            <span className="ms-auto flex shrink-0 items-center gap-1.5">
              {result ? (
                result.isError ? (
                  <Badge variant="destructive">
                    <CircleAlert />
                    失败
                  </Badge>
                ) : (
                  <Badge variant="secondary">成功</Badge>
                )
              ) : streaming ? (
                <span className="flex items-center gap-1 text-muted-foreground">
                  <Loader2 className="size-3 animate-spin" />
                  执行中
                </span>
              ) : (
                <Badge variant="outline">未返回</Badge>
              )}
              {result ? (
                <span className="text-muted-foreground">{result.durationMs} ms</span>
              ) : null}
              <ChevronDown
                className={cn(
                  "size-3.5 text-muted-foreground transition-transform",
                  open && "rotate-180",
                )}
              />
            </span>
          </button>
        </CollapsibleTrigger>
        <CollapsibleContent>
          <div className="space-y-2 border-t px-2.5 py-2">
            {argumentsText ? (
              <CodeBlock size="sm">
                <CodeBlockHeader>
                  <CodeBlockTitle>参数</CodeBlockTitle>
                  <CodeBlockActions>
                    <CodeBlockCopyButton value={argumentsText} />
                  </CodeBlockActions>
                </CodeBlockHeader>
                <CodeBlockContent wrap className="max-h-48">
                  {argumentsText}
                </CodeBlockContent>
              </CodeBlock>
            ) : null}
            {result ? (
              <CodeBlock size="sm" variant={result.isError ? "destructive" : "default"}>
                <CodeBlockHeader>
                  <CodeBlockTitle>{result.isError ? "错误" : "结果"}</CodeBlockTitle>
                  <CodeBlockActions>
                    <CodeBlockCopyButton value={result.output || "（空）"} />
                  </CodeBlockActions>
                </CodeBlockHeader>
                <CodeBlockContent wrap className="max-h-48">
                  {result.output || "（空）"}
                </CodeBlockContent>
              </CodeBlock>
            ) : null}
            {!argumentsText && !result ? (
              <p className="text-muted-foreground">等待工具返回结果…</p>
            ) : null}
          </div>
        </CollapsibleContent>
      </div>
    </Collapsible>
  );
}

function formatJson(value: unknown): string {
  if (value == null) {
    return "";
  }
  if (typeof value === "string") {
    return value;
  }
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

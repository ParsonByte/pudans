import { type FormEvent, useId, useRef, useState } from "react";

import { useMutation } from "@tanstack/react-query";
import { CircleAlert, Eraser, Loader2, Play } from "lucide-react";
import { toast } from "sonner";

import { FormField } from "@/components/form-field";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { linesToList } from "@/modules/workspace/ai/library/lib/model";

import { type AiRuntimeExecResult, execRuntimeCommand, resolveAgentRuntimeError } from "../lib/api";
import { formatExecDuration, formatLogTime } from "../lib/model";

const COMMAND_HISTORY_LIMIT = 20;

type CommandHistoryEntry = AiRuntimeExecResult & {
  id: number;
  command: string;
  args: string[];
  workdir?: string;
  executedAt: string;
};

type CommandConsoleProps = {
  agent: string;
  running: boolean;
  defaultWorkdir: string;
  timeoutLimit?: number | null;
};

export function CommandConsole({
  agent,
  running,
  defaultWorkdir,
  timeoutLimit,
}: CommandConsoleProps) {
  const fieldId = useId();
  const nextEntryId = useRef(0);
  const [command, setCommand] = useState("");
  const [argsText, setArgsText] = useState("");
  const [workdir, setWorkdir] = useState(defaultWorkdir);
  const [timeoutText, setTimeoutText] = useState("");
  const [history, setHistory] = useState<CommandHistoryEntry[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const execMutation = useMutation({
    mutationFn: (request: {
      command: string;
      args: string[];
      workdir?: string;
      timeoutSeconds?: number;
    }) => execRuntimeCommand(agent, request),
    onSuccess: (result, request) => {
      nextEntryId.current += 1;
      const entry: CommandHistoryEntry = {
        ...result,
        id: nextEntryId.current,
        command: request.command,
        args: request.args,
        workdir: request.workdir,
        executedAt: new Date().toISOString(),
      };
      setHistory((current) => [entry, ...current].slice(0, COMMAND_HISTORY_LIMIT));
      setSelectedId(entry.id);
      setError(null);
    },
    onError: (mutationError) => {
      const message = resolveAgentRuntimeError(mutationError);
      setError(message);
      toast.error(message);
    },
  });

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const trimmedCommand = command.trim();
    if (!trimmedCommand) {
      toast.error("请输入要执行的命令。");
      return;
    }
    const timeoutValue = timeoutText.trim();
    const timeoutSeconds = timeoutValue ? Number(timeoutValue) : undefined;
    if (
      timeoutSeconds !== undefined &&
      (!Number.isInteger(timeoutSeconds) || timeoutSeconds <= 0)
    ) {
      toast.error("超时时间必须是大于 0 的整数。");
      return;
    }
    execMutation.mutate({
      command: trimmedCommand,
      args: linesToList(argsText),
      workdir: workdir.trim() || undefined,
      timeoutSeconds,
    });
  };

  const selected = history.find((entry) => entry.id === selectedId) ?? history[0];

  return (
    <div className="space-y-4">
      {!running ? (
        <Alert>
          <CircleAlert />
          <AlertTitle>沙箱未运行</AlertTitle>
          <AlertDescription>先启动运行时，命令才会在 Agent 沙箱内执行。</AlertDescription>
        </Alert>
      ) : null}

      <form onSubmit={submit} className="space-y-3">
        <FormField label="命令" htmlFor={`${fieldId}-command`} hint="例如 bash、python、ls。">
          <Input
            id={`${fieldId}-command`}
            value={command}
            onChange={(event) => setCommand(event.target.value)}
            placeholder="bash"
            className="font-mono"
            disabled={!running || execMutation.isPending}
          />
        </FormField>
        <FormField label="参数" htmlFor={`${fieldId}-args`} hint="每行一个参数；留空表示无参数。">
          <Textarea
            id={`${fieldId}-args`}
            value={argsText}
            onChange={(event) => setArgsText(event.target.value)}
            placeholder={"-lc\npwd && ls -la"}
            className="min-h-20 font-mono text-xs"
            disabled={!running || execMutation.isPending}
          />
        </FormField>
        <div className="grid gap-3 sm:grid-cols-2">
          <FormField label="工作目录" htmlFor={`${fieldId}-workdir`} hint="留空使用默认工作目录。">
            <Input
              id={`${fieldId}-workdir`}
              value={workdir}
              onChange={(event) => setWorkdir(event.target.value)}
              placeholder={defaultWorkdir}
              className="font-mono"
              disabled={!running || execMutation.isPending}
            />
          </FormField>
          <FormField
            label="超时（秒）"
            htmlFor={`${fieldId}-timeout`}
            hint={
              timeoutLimit
                ? `超过 ${timeoutLimit} 秒会被运行时上限截断。`
                : "留空使用运行时默认超时。"
            }
          >
            <Input
              id={`${fieldId}-timeout`}
              value={timeoutText}
              onChange={(event) => setTimeoutText(event.target.value)}
              placeholder={timeoutLimit ? String(timeoutLimit) : "300"}
              inputMode="numeric"
              disabled={!running || execMutation.isPending}
            />
          </FormField>
        </div>
        <Button type="submit" size="sm" disabled={!running || execMutation.isPending}>
          {execMutation.isPending ? <Loader2 className="animate-spin" /> : <Play />}
          执行命令
        </Button>
      </form>

      {error ? (
        <Alert variant="destructive">
          <CircleAlert />
          <AlertTitle>命令执行失败</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      ) : null}

      {selected ? (
        <div className="space-y-3 border p-4">
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant={selected.exitCode === 0 ? "secondary" : "destructive"}>
              退出码 {selected.exitCode}
            </Badge>
            <Badge variant="outline">耗时 {formatExecDuration(selected.durationMs)}</Badge>
            <span className="text-xs text-muted-foreground">
              {formatLogTime(selected.executedAt)}
            </span>
          </div>
          <p className="font-mono text-xs break-all text-muted-foreground">
            {selected.command} {selected.args.join(" ")}
          </p>
          <OutputBlock label="标准输出" value={selected.stdout} emptyText="没有标准输出。" />
          <OutputBlock label="标准错误" value={selected.stderr} emptyText="没有标准错误。" />
        </div>
      ) : null}

      <div className="space-y-2">
        <div className="flex items-center justify-between">
          <p className="text-sm font-medium">历史记录</p>
          <Button
            type="button"
            variant="ghost"
            size="xs"
            onClick={() => {
              setHistory([]);
              setSelectedId(null);
            }}
            disabled={history.length === 0}
          >
            <Eraser />
            清空
          </Button>
        </div>
        {history.length === 0 ? (
          <p className="border border-dashed px-3 py-6 text-center text-xs text-muted-foreground">
            尚无执行记录。
          </p>
        ) : (
          <div className="space-y-1">
            {history.map((entry) => (
              <button
                key={entry.id}
                type="button"
                onClick={() => setSelectedId(entry.id)}
                className={`flex w-full items-center gap-2 border px-2 py-1.5 text-start text-xs transition-colors hover:bg-accent ${
                  entry.id === selected?.id ? "border-primary/40 bg-accent/40" : ""
                }`}
              >
                <Badge variant={entry.exitCode === 0 ? "secondary" : "destructive"}>
                  {entry.exitCode}
                </Badge>
                <span className="min-w-0 flex-1 truncate font-mono">
                  {entry.command} {entry.args.join(" ")}
                </span>
                <span className="shrink-0 text-muted-foreground">
                  {formatLogTime(entry.executedAt)}
                </span>
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

type OutputBlockProps = {
  label: string;
  value: string;
  emptyText: string;
};

function OutputBlock({ label, value, emptyText }: OutputBlockProps) {
  return (
    <div className="space-y-1">
      <p className="text-xs font-medium text-muted-foreground">{label}</p>
      {value ? (
        <pre className="max-h-56 overflow-auto border bg-muted/40 p-3 font-mono text-xs break-words whitespace-pre-wrap">
          {value}
        </pre>
      ) : (
        <p className="text-xs text-muted-foreground">{emptyText}</p>
      )}
    </div>
  );
}

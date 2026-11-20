import { useCallback, useEffect, useRef, useState } from "react";

import { CircleAlert, Eraser, Pause, Play, RefreshCw } from "lucide-react";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";

import { type AiRuntimeLogEvent, streamRuntimeLogs } from "../lib/api";
import { LOG_LINE_LIMIT, formatLogTime, logLineKey } from "../lib/model";

type ConnectionState = "idle" | "streaming" | "paused" | "done" | "error";

const CONNECTION_LABELS: Record<ConnectionState, string> = {
  idle: "未连接",
  streaming: "实时接收中",
  paused: "已暂停",
  done: "已完成",
  error: "已断开",
};

type LogConsoleProps = {
  agent: string;
  running: boolean;
  available: boolean;
};

export function LogConsole({ agent, running, available }: LogConsoleProps) {
  const [follow, setFollow] = useState(true);
  const [autoScroll, setAutoScroll] = useState(true);
  const [lines, setLines] = useState<AiRuntimeLogEvent[]>([]);
  const [status, setStatus] = useState<ConnectionState>("idle");
  const [error, setError] = useState<string | null>(null);
  const controllerRef = useRef<AbortController | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  const connect = useCallback(async () => {
    if (!available) {
      return;
    }
    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    setStatus("streaming");
    setError(null);
    try {
      await streamRuntimeLogs(agent, {
        follow,
        signal: controller.signal,
        onEvent: (event) => setLines((current) => appendLogLine(current, event)),
      });
      if (!controller.signal.aborted) {
        setStatus("done");
      }
    } catch (streamError) {
      if (controller.signal.aborted) {
        return;
      }
      setStatus("error");
      setError(streamError instanceof Error ? streamError.message : "日志流连接失败，请稍后重试。");
    }
  }, [agent, available, follow]);

  useEffect(() => {
    if (!available) {
      return;
    }
    const timer = window.setTimeout(() => {
      void connect();
    }, 0);
    return () => {
      window.clearTimeout(timer);
      controllerRef.current?.abort();
    };
  }, [available, connect]);

  useEffect(() => {
    if (!autoScroll) {
      return;
    }
    const element = scrollRef.current;
    if (element) {
      element.scrollTop = element.scrollHeight;
    }
  }, [autoScroll, lines]);

  if (!available) {
    return (
      <p className="rounded-lg border border-dashed px-3 py-8 text-center text-xs text-muted-foreground">
        运行时尚未创建，启动运行时后即可查看沙箱日志。
      </p>
    );
  }

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
        <div className="flex items-center gap-2">
          <Switch
            id="runtime-log-follow"
            checked={follow}
            onCheckedChange={setFollow}
            aria-label="实时跟随日志"
          />
          <Label htmlFor="runtime-log-follow">实时跟随</Label>
        </div>
        <div className="flex items-center gap-2">
          <Switch
            id="runtime-log-autoscroll"
            checked={autoScroll}
            onCheckedChange={setAutoScroll}
            aria-label="自动滚动"
          />
          <Label htmlFor="runtime-log-autoscroll">自动滚动</Label>
        </div>
        <Badge variant={status === "error" ? "destructive" : "outline"}>
          {CONNECTION_LABELS[status]}
        </Badge>
        <div className="ms-auto flex items-center gap-2">
          {status === "streaming" ? (
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => {
                controllerRef.current?.abort();
                setStatus("paused");
              }}
            >
              <Pause />
              暂停
            </Button>
          ) : (
            <Button type="button" variant="outline" size="sm" onClick={() => void connect()}>
              <Play />
              继续
            </Button>
          )}
          <Button type="button" variant="outline" size="sm" onClick={() => void connect()}>
            <RefreshCw />
            重连
          </Button>
          <Button type="button" variant="ghost" size="sm" onClick={() => setLines([])}>
            <Eraser />
            清空
          </Button>
        </div>
      </div>

      {!running ? (
        <p className="text-xs text-muted-foreground">
          沙箱未运行，实时跟随只会读取历史日志；启动运行时后会自动追加新日志。
        </p>
      ) : null}

      {error ? (
        <Alert variant="destructive">
          <CircleAlert />
          <AlertTitle>日志流已断开</AlertTitle>
          <AlertDescription className="space-y-2">
            <p>{error}</p>
            <Button type="button" variant="outline" size="sm" onClick={() => void connect()}>
              重新连接
            </Button>
          </AlertDescription>
        </Alert>
      ) : null}

      <div
        ref={scrollRef}
        className="h-80 overflow-auto rounded-lg border bg-muted/30 p-3 font-mono text-xs"
      >
        {lines.length === 0 ? (
          <p className="py-8 text-center text-muted-foreground">
            暂无日志。启动沙箱或执行命令后查看输出。
          </p>
        ) : (
          <div className="space-y-0.5">
            {lines.map((line, index) => (
              <div
                key={`${logLineKey(line)}-${index}`}
                className={`flex gap-2 ${line.stream === "stderr" ? "text-destructive" : ""}`}
              >
                <span className="shrink-0 text-muted-foreground">
                  {formatLogTime(line.timestamp)}
                </span>
                <span
                  className={`w-8 shrink-0 uppercase ${
                    line.stream === "stderr" ? "text-destructive" : "text-muted-foreground"
                  }`}
                >
                  {line.stream === "stderr" ? "ERR" : "OUT"}
                </span>
                <span className="min-w-0 break-words whitespace-pre-wrap">{line.line}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function appendLogLine(
  current: AiRuntimeLogEvent[],
  event: AiRuntimeLogEvent,
): AiRuntimeLogEvent[] {
  const key = logLineKey(event);
  if (current.some((item) => logLineKey(item) === key)) {
    return current;
  }
  const next = [...current, event];
  return next.length > LOG_LINE_LIMIT ? next.slice(next.length - LOG_LINE_LIMIT) : next;
}

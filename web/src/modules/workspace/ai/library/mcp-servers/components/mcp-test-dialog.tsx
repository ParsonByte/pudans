import { DetailDialog } from "@/components/detail-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";

import type { McpTestResult } from "../lib/api";

type McpTestDialogProps = {
  serverName: string;
  result: McpTestResult;
  onClose: () => void;
};

export function McpTestDialog({ serverName, result, onClose }: McpTestDialogProps) {
  return (
    <DetailDialog
      open
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
      title={`测试结果 · ${serverName}`}
      description="探测结果会同时写入服务器状态，用于列表中的健康度展示。"
      size="lg"
      footer={
        <Button type="button" variant="outline" onClick={onClose}>
          关闭
        </Button>
      }
    >
      <div className="flex flex-wrap items-center gap-2">
        <Badge variant={result.healthy ? "secondary" : "destructive"}>
          {result.healthy ? "连接正常" : "连接异常"}
        </Badge>
        <span className="text-xs text-muted-foreground">{result.toolCount} 个可用工具</span>
      </div>

      {result.message ? <p className="text-sm">{result.message}</p> : null}

      {result.tools.length === 0 ? (
        <p className="border px-3 py-6 text-center text-xs text-muted-foreground">
          该服务器未返回任何工具。
        </p>
      ) : (
        <ScrollArea className="max-h-64 border">
          <ul className="divide-y">
            {result.tools.map((tool) => (
              <li key={tool.name} className="space-y-1 px-3 py-2">
                <p className="font-mono text-xs font-medium">{tool.name}</p>
                {tool.description ? (
                  <p className="text-xs text-muted-foreground">{tool.description}</p>
                ) : null}
              </li>
            ))}
          </ul>
        </ScrollArea>
      )}
    </DetailDialog>
  );
}

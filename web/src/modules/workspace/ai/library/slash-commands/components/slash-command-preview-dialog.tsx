import { useState } from "react";

import { useMutation } from "@tanstack/react-query";
import { Loader2, Play } from "lucide-react";
import { toast } from "sonner";

import { MarkdownContent } from "@/components/content/markdown";
import { DialogFormShell } from "@/components/dialog-form-shell";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

import { useAgentLabels } from "../../lib/agents";
import { resolveLibraryApiError } from "../../lib/api";
import { type SlashCommandItem, resolveSlashCommand } from "../lib/api";

type SlashCommandPreviewDialogProps = {
  command: SlashCommandItem;
  onClose: () => void;
};

export function SlashCommandPreviewDialog({ command, onClose }: SlashCommandPreviewDialogProps) {
  const agentLabels = useAgentLabels();
  const agents = command.spec.agents ?? [];
  const enabled = command.spec.enabled !== false;
  const [agent, setAgent] = useState(agents[0] ?? "");
  const [argumentsText, setArgumentsText] = useState("");

  const resolveMutation = useMutation({
    mutationFn: () => {
      const suffix = argumentsText.trim();
      return resolveSlashCommand(agent, `/${command.spec.name}${suffix ? ` ${suffix}` : ""}`);
    },
    onError: (error) => toast.error(resolveLibraryApiError(error, "斜杠命令")),
  });

  return (
    <DialogFormShell
      open
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
      title={`/${command.spec.name}`}
      description={command.spec.description || "查看提示词模板并解析参数展开结果。"}
      size="3xl"
      footer={
        <Button type="button" variant="outline" onClick={onClose}>
          关闭
        </Button>
      }
    >
      <div className="space-y-6">
        <section className="space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="text-sm font-semibold">提示词模板</h3>
            <Badge variant={enabled ? "secondary" : "outline"}>
              {enabled ? "已启用" : "已停用"}
            </Badge>
          </div>
          <div className="border bg-muted/30 p-4">
            <MarkdownContent content={command.spec.prompt} />
          </div>
          <p className="text-xs text-muted-foreground">
            参数提示：{command.spec.argumentHint || "未设置"}
          </p>
        </section>

        <section className="space-y-3">
          <h3 className="text-sm font-semibold">解析预览</h3>
          {agents.length === 0 ? (
            <p className="border border-dashed p-4 text-sm text-muted-foreground">
              该命令未绑定 Agent，无法解析。请先在编辑中绑定至少一个 Agent。
            </p>
          ) : (
            <>
              <div className="grid gap-3 sm:grid-cols-2">
                <label className="space-y-1 text-sm">
                  <span className="text-xs text-muted-foreground">Agent</span>
                  <Select value={agent} onValueChange={setAgent}>
                    <SelectTrigger className="w-full">
                      <SelectValue placeholder="选择 Agent" />
                    </SelectTrigger>
                    <SelectContent>
                      {agents.map((name) => (
                        <SelectItem key={name} value={name}>
                          {agentLabels.get(name) ?? name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </label>
                <label className="space-y-1 text-sm">
                  <span className="text-xs text-muted-foreground">参数</span>
                  <Input
                    value={argumentsText}
                    onChange={(event) => setArgumentsText(event.target.value)}
                    placeholder={command.spec.argumentHint || "输入命令参数"}
                  />
                </label>
              </div>
              {!enabled ? (
                <p className="text-xs text-muted-foreground">命令已停用，恢复启用后才能解析。</p>
              ) : null}
              <Button
                type="button"
                size="sm"
                disabled={!agent || !enabled || resolveMutation.isPending}
                onClick={() => resolveMutation.mutate()}
              >
                {resolveMutation.isPending ? <Loader2 className="animate-spin" /> : <Play />}
                解析预览
              </Button>
            </>
          )}

          {resolveMutation.isError ? (
            <p className="text-xs text-destructive">
              {resolveLibraryApiError(resolveMutation.error, "斜杠命令")}
            </p>
          ) : null}

          {resolveMutation.data ? (
            <div className="space-y-2">
              <p className="font-mono text-xs text-muted-foreground">
                /{resolveMutation.data.name} 展开结果
              </p>
              <div className="border bg-muted/30 p-4">
                <MarkdownContent content={resolveMutation.data.expandedPrompt} />
              </div>
            </div>
          ) : null}
        </section>
      </div>
    </DialogFormShell>
  );
}

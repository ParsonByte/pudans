import { useState } from "react";

import { FileJson, Loader2 } from "lucide-react";

import { DialogFormShell } from "@/components/dialog-form-shell";
import { FormField } from "@/components/form-field";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";

import type { MemoryAssetView, MemoryImportMessage } from "../lib/api";
import {
  MEMORY_IMPORT_LIMIT,
  type MemoryImportParseResult,
  memoryAssetTitle,
  parseMemoryImportPayload,
} from "../lib/model";

type ImportMemoryDialogProps = {
  assets: MemoryAssetView[];
  initialAssetName?: string;
  pending: boolean;
  onConfirm: (assetName: string, messages: MemoryImportMessage[]) => void;
  onClose: () => void;
};

const ROLE_LABELS: Record<MemoryImportMessage["role"], string> = {
  user: "用户",
  assistant: "助手",
};

export function ImportMemoryDialog({
  assets,
  initialAssetName,
  pending,
  onConfirm,
  onClose,
}: ImportMemoryDialogProps) {
  const [targetName, setTargetName] = useState(
    initialAssetName && assets.some((asset) => asset.name === initialAssetName)
      ? initialAssetName
      : "",
  );
  const [content, setContent] = useState("");
  const [fileName, setFileName] = useState("");
  const [plainText, setPlainText] = useState(false);
  const [parseResult, setParseResult] = useState<MemoryImportParseResult | null>(null);

  function evaluate(text: string, allowPlainText: boolean) {
    setContent(text);
    if (!text.trim()) {
      setParseResult(null);
      return;
    }
    setParseResult(parseMemoryImportPayload(text, allowPlainText));
  }

  async function handleFile(file: File | undefined) {
    if (!file) {
      return;
    }
    const lowerName = file.name.toLowerCase();
    if (!/\.(json|txt|md)$/.test(lowerName)) {
      setFileName(file.name);
      setParseResult({ ok: false, error: "仅支持 .json、.txt 或 .md 文件。" });
      return;
    }
    const allowPlainText = lowerName.endsWith(".txt") || lowerName.endsWith(".md");
    setFileName(file.name);
    setPlainText(allowPlainText);
    try {
      const text = await file.text();
      evaluate(text, allowPlainText);
    } catch {
      setParseResult({ ok: false, error: "读取文件失败，请重试。" });
    }
  }

  const messages = parseResult?.ok ? parseResult.messages : [];
  const canSubmit = Boolean(targetName) && parseResult?.ok && !pending;

  return (
    <DialogFormShell
      open
      onOpenChange={(open) => {
        if (!open && !pending) {
          onClose();
        }
      }}
      title="导入记忆"
      description={`粘贴 JSON 消息数组或上传 .json/.txt/.md 文件，导入后写入所选记忆块的 L0 并触发 L1 抽取，最多 ${MEMORY_IMPORT_LIMIT} 条。`}
      size="xl"
      footer={
        <>
          <Button type="button" variant="outline" onClick={onClose} disabled={pending}>
            取消
          </Button>
          <Button
            type="button"
            disabled={!canSubmit || assets.length === 0}
            onClick={() => onConfirm(targetName, messages)}
          >
            {pending ? <Loader2 className="animate-spin" /> : null}
            导入 {messages.length > 0 ? `${messages.length} 条` : ""}
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        {assets.length === 0 ? (
          <Alert variant="destructive">
            <AlertTitle>没有可导入的记忆资产</AlertTitle>
            <AlertDescription>请先创建记忆块，再执行导入。</AlertDescription>
          </Alert>
        ) : (
          <FormField label="目标记忆块" htmlFor="memory-import-target">
            <Select value={targetName} onValueChange={setTargetName}>
              <SelectTrigger id="memory-import-target" className="w-full">
                <SelectValue placeholder="选择记忆块" />
              </SelectTrigger>
              <SelectContent>
                {assets.map((asset) => (
                  <SelectItem key={asset.name} value={asset.name}>
                    {memoryAssetTitle(asset)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </FormField>
        )}

        <FormField label="从文件导入" htmlFor="memory-import-file">
          <Input
            id="memory-import-file"
            type="file"
            accept=".json,.txt,.md,application/json,text/plain,text/markdown"
            disabled={pending}
            onChange={(event) => void handleFile(event.target.files?.[0])}
          />
          {fileName ? (
            <p className="flex items-center gap-1 text-xs text-muted-foreground">
              <FileJson className="size-3.5" />
              {fileName}
            </p>
          ) : null}
        </FormField>

        <FormField label="粘贴内容" htmlFor="memory-import-content">
          <Textarea
            id="memory-import-content"
            value={content}
            onChange={(event) => evaluate(event.target.value, plainText)}
            disabled={pending}
            className="min-h-40 font-mono text-xs"
            placeholder='[{"role":"user","content":"..."},{"role":"assistant","content":"..."}]'
          />
          <label className="flex items-center gap-2 text-xs text-muted-foreground">
            <Checkbox
              checked={plainText}
              onCheckedChange={(checked) => {
                const next = checked === true;
                setPlainText(next);
                evaluate(content, next);
              }}
              disabled={pending}
            />
            按纯文本导入（单条用户消息）
          </label>
        </FormField>

        {parseResult && !parseResult.ok ? (
          <Alert variant="destructive">
            <AlertTitle>校验失败</AlertTitle>
            <AlertDescription>{parseResult.error}</AlertDescription>
          </Alert>
        ) : null}
        {parseResult?.ok ? (
          <Alert>
            <AlertTitle>校验通过，共 {messages.length} 条消息</AlertTitle>
            <AlertDescription>
              <span>
                用户 {messages.filter((message) => message.role === "user").length} 条 · 助手{" "}
                {messages.filter((message) => message.role === "assistant").length} 条
              </span>
              <span className="mt-2 block max-h-24 space-y-1 overflow-y-auto">
                {messages.slice(0, 3).map((message, index) => (
                  <span key={index} className="block truncate">
                    {ROLE_LABELS[message.role]}：{message.content}
                  </span>
                ))}
                {messages.length > 3 ? (
                  <span className="block text-muted-foreground">
                    其余 {messages.length - 3} 条省略
                  </span>
                ) : null}
              </span>
            </AlertDescription>
          </Alert>
        ) : null}
      </div>
    </DialogFormShell>
  );
}

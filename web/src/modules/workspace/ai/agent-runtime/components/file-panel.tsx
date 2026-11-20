import { type FormEvent, useId, useState } from "react";

import { useMutation } from "@tanstack/react-query";
import { CircleAlert, FileSearch, Loader2, Save } from "lucide-react";
import { toast } from "sonner";

import { FormField } from "@/components/form-field";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
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
import { Textarea } from "@/components/ui/textarea";
import { formatFileSize } from "@/modules/workspace/console/storage-policies/lib/model";

import {
  type AiRuntimeFile,
  readRuntimeFile,
  resolveAgentRuntimeError,
  writeRuntimeFile,
} from "../lib/api";
import { FILE_ENCODING_BASE64, FILE_ENCODING_TEXT, fileEncodingLabel } from "../lib/model";

type FilePanelProps = {
  agent: string;
  running: boolean;
};

export function FilePanel({ agent, running }: FilePanelProps) {
  const fieldId = useId();
  const [path, setPath] = useState("");
  const [loadedFile, setLoadedFile] = useState<AiRuntimeFile | null>(null);
  const [content, setContent] = useState("");
  const [encoding, setEncoding] = useState<string>(FILE_ENCODING_TEXT);
  const [error, setError] = useState<string | null>(null);

  const readMutation = useMutation({
    mutationFn: (target: string) => readRuntimeFile(agent, target),
    onSuccess: (file) => {
      setLoadedFile(file);
      setPath(file.path);
      setContent(file.content);
      setEncoding(
        file.encoding?.toLowerCase() === FILE_ENCODING_BASE64
          ? FILE_ENCODING_BASE64
          : FILE_ENCODING_TEXT,
      );
      setError(null);
    },
    onError: (readError) => {
      const message = resolveAgentRuntimeError(readError);
      setError(message);
      toast.error(message);
    },
  });

  const writeMutation = useMutation({
    mutationFn: (request: { path: string; encoding: string; content: string }) =>
      writeRuntimeFile(agent, request),
    onSuccess: () => {
      toast.success("文件已写入沙箱。");
    },
    onError: (writeError) => toast.error(resolveAgentRuntimeError(writeError)),
  });

  const submitRead = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const target = path.trim();
    if (!target) {
      toast.error("请输入要读取的文件路径。");
      return;
    }
    readMutation.mutate(target);
  };

  const submitWrite = () => {
    const target = path.trim();
    if (!target) {
      toast.error("请输入要写入的文件路径。");
      return;
    }
    writeMutation.mutate({ path: target, encoding, content });
  };

  const isBinary = loadedFile?.encoding?.toLowerCase() === FILE_ENCODING_BASE64;

  return (
    <div className="space-y-4">
      {!running ? (
        <Alert>
          <CircleAlert />
          <AlertTitle>沙箱未运行</AlertTitle>
          <AlertDescription>先启动运行时，才能读取或写入沙箱文件。</AlertDescription>
        </Alert>
      ) : null}

      <form onSubmit={submitRead} className="flex flex-col gap-2 sm:flex-row sm:items-end">
        <div className="flex-1">
          <FormField
            label="文件路径"
            htmlFor={`${fieldId}-path`}
            hint="相对路径基于 Agent 工作目录，也可以填写绝对路径。"
          >
            <Input
              id={`${fieldId}-path`}
              value={path}
              onChange={(event) => setPath(event.target.value)}
              placeholder="/workspace/README.md"
              className="font-mono"
              disabled={!running || readMutation.isPending}
            />
          </FormField>
        </div>
        <Button type="submit" variant="outline" disabled={!running || readMutation.isPending}>
          {readMutation.isPending ? <Loader2 className="animate-spin" /> : <FileSearch />}
          读取
        </Button>
      </form>

      {error ? (
        <Alert variant="destructive">
          <CircleAlert />
          <AlertTitle>文件读取失败</AlertTitle>
          <AlertDescription className="space-y-2">
            <p>{error}</p>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => readMutation.mutate(path.trim())}
              disabled={!running || !path.trim() || readMutation.isPending}
            >
              重试
            </Button>
          </AlertDescription>
        </Alert>
      ) : null}

      {loadedFile ? (
        <div className="space-y-3">
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant="outline">{fileEncodingLabel(loadedFile.encoding)}</Badge>
            <Badge variant="outline">{formatFileSize(loadedFile.size)}</Badge>
            <span className="min-w-0 truncate font-mono text-xs text-muted-foreground">
              {loadedFile.path}
            </span>
          </div>
          <div className="grid gap-3 sm:grid-cols-[160px_1fr]">
            <FormField label="写入编码" htmlFor={`${fieldId}-encoding`}>
              <Select value={encoding} onValueChange={setEncoding} disabled={!running}>
                <SelectTrigger id={`${fieldId}-encoding`} className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={FILE_ENCODING_TEXT}>文本（UTF-8）</SelectItem>
                  <SelectItem value={FILE_ENCODING_BASE64}>Base64</SelectItem>
                </SelectContent>
              </Select>
            </FormField>
            <div className="space-y-1">
              <p className="text-xs text-muted-foreground">
                {isBinary
                  ? "该文件不是 UTF-8 文本，读取结果以 Base64 展示。"
                  : "编辑下方内容后保存，将覆盖沙箱中的同名文件。"}
              </p>
            </div>
          </div>
          <FormField label="文件内容" htmlFor={`${fieldId}-content`}>
            <Textarea
              id={`${fieldId}-content`}
              value={content}
              onChange={(event) => setContent(event.target.value)}
              className="min-h-64 font-mono text-xs"
              spellCheck={false}
              disabled={!running || writeMutation.isPending}
            />
          </FormField>
          <Button
            type="button"
            onClick={submitWrite}
            disabled={!running || writeMutation.isPending || !path.trim()}
          >
            {writeMutation.isPending ? <Loader2 className="animate-spin" /> : <Save />}
            保存到沙箱
          </Button>
        </div>
      ) : null}
    </div>
  );
}

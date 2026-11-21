import { type ChangeEvent, useState } from "react";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Download, Loader2, Upload } from "lucide-react";
import { toast } from "sonner";

import { DialogFormShell } from "@/components/dialog-form-shell";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { downloadCsv } from "@/lib/csv";

import { createLocalUsers, resolveUserApiError } from "../lib/api";
import { notifyBatchResult } from "../lib/notify-batch-result";
import { type ParsedUserImport, parseUserImport, userImportTemplate } from "../lib/user-csv";

type ImportUsersDialogProps = {
  onClose: () => void;
};

export function ImportUsersDialog({ onClose }: ImportUsersDialogProps) {
  const queryClient = useQueryClient();
  const [fileName, setFileName] = useState("");
  const [parsed, setParsed] = useState<ParsedUserImport | null>(null);

  const mutation = useMutation({
    mutationFn: () => createLocalUsers(parsed?.users ?? []),
    onSuccess: async (result) => {
      await queryClient.invalidateQueries({ queryKey: ["users"] });
      notifyBatchResult(result);
      onClose();
    },
    onError: (error) => toast.error(resolveUserApiError(error)),
  });

  const handleFileChange = async (event: ChangeEvent<HTMLInputElement>) => {
    const input = event.currentTarget;
    const file = input.files?.[0];
    input.value = "";
    if (!file) {
      return;
    }

    setFileName(file.name);
    try {
      setParsed(parseUserImport(await file.text()));
    } catch {
      setParsed({ users: [], errors: ["无法读取该文件。"] });
    }
  };

  const canImport = Boolean(parsed && parsed.users.length > 0 && parsed.errors.length === 0);
  const previewUsers = parsed?.users.slice(0, 5) ?? [];

  return (
    <DialogFormShell
      open
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
      icon={<Upload />}
      title="导入用户"
      description="上传 CSV 文件批量创建本地账号；姓名、邮箱、手机号和头像会在绑定飞书后同步。"
      size="2xl"
      maxHeightRatio={0.6}
      footer={
        <>
          <Button type="button" variant="outline" onClick={onClose} disabled={mutation.isPending}>
            取消
          </Button>
          <Button
            type="button"
            onClick={() => mutation.mutate()}
            disabled={!canImport || mutation.isPending}
          >
            {mutation.isPending ? <Loader2 className="animate-spin" /> : <Upload />}
            导入 {parsed?.users.length ?? 0} 个用户
          </Button>
        </>
      }
    >
      <div className="space-y-5">
        <div className="flex flex-wrap items-center gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => downloadCsv("user-import-template.csv", userImportTemplate())}
          >
            <Download />
            下载 CSV 模板
          </Button>
          <p className="text-xs text-muted-foreground">
            模板列为：用户名、初始密码、用户组（用户组可留空）。
          </p>
        </div>

        <div className="space-y-2">
          <Input
            type="file"
            accept=".csv,text/csv"
            onChange={(event) => void handleFileChange(event)}
          />
          {fileName ? (
            <p className="text-xs text-muted-foreground">
              已读取 {fileName}，共 {parsed?.users.length ?? 0} 个有效用户。
            </p>
          ) : null}
        </div>

        {parsed?.errors.length ? (
          <div className="border border-destructive/30 bg-destructive/5 p-3">
            <p className="text-xs font-medium text-destructive">
              发现 {parsed.errors.length} 个问题，请修正文件后重新上传。
            </p>
            <ul className="mt-2 list-disc space-y-1 ps-4 text-xs text-destructive">
              {parsed.errors.slice(0, 8).map((error, index) => (
                <li key={`${index}-${error}`}>{error}</li>
              ))}
            </ul>
            {parsed.errors.length > 8 ? (
              <p className="mt-2 text-xs text-destructive">
                还有 {parsed.errors.length - 8} 个问题未显示。
              </p>
            ) : null}
          </div>
        ) : null}

        {previewUsers.length > 0 ? (
          <div className="space-y-2">
            <p className="text-xs font-medium text-muted-foreground">
              预览前 {previewUsers.length} 个用户
            </p>
            <div className="overflow-hidden border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>用户名</TableHead>
                    <TableHead>用户组</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {previewUsers.map((user) => (
                    <TableRow key={user.username}>
                      <TableCell className="font-mono text-xs">{user.username}</TableCell>
                      <TableCell>{user.userGroup || "默认用户组"}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
            {parsed && parsed.users.length > previewUsers.length ? (
              <p className="text-xs text-muted-foreground">
                还有 {parsed.users.length - previewUsers.length} 个用户未预览。
              </p>
            ) : null}
          </div>
        ) : null}
      </div>
    </DialogFormShell>
  );
}

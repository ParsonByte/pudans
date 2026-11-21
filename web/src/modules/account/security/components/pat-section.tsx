"use client";

import { useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Ban, Pencil, Plus, RotateCcw } from "lucide-react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";
import { useSudo } from "@/components/sudo/sudo-provider";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { resolveApiError } from "@/lib/api-error";
import { CopyField } from "@/modules/workspace/ai/gateway/components/copy-field";
import {
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";
import {
  ConsoleBlockSkeleton,
  ConsoleState,
} from "@/modules/workspace/console/components/console-state";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";

import {
  type CreatedPat,
  PAT_LIST_QUERY_KEY,
  type PatState,
  fetchPatList,
  patDisplayName,
  patState,
  restorePat,
  revokePat,
} from "../lib/pats";
import { PatDialog } from "./pat-dialog";

const ERROR_MESSAGES = {
  notFound: "个人访问令牌不存在或已删除。",
  conflict: "操作失败，请稍后重试。",
};

const STATE_LABELS: Record<PatState, string> = {
  ACTIVE: "有效",
  REVOKED: "已撤销",
  EXPIRED: "已过期",
};

type PatAction = {
  type: "revoke" | "restore";
  name: string;
  label: string;
};

export function PersonalAccessTokensSection() {
  const { requireSudo } = useSudo();
  const queryClient = useQueryClient();
  const listQuery = useQuery({
    queryKey: PAT_LIST_QUERY_KEY,
    queryFn: fetchPatList,
    retry: false,
  });
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingName, setEditingName] = useState<string | null>(null);
  const [createdPat, setCreatedPat] = useState<CreatedPat | null>(null);
  const [action, setAction] = useState<PatAction | null>(null);

  const actionMutation = useMutation({
    mutationFn: async (target: PatAction) => {
      await requireSudo();
      return target.type === "revoke" ? revokePat(target.name) : restorePat(target.name);
    },
    onSuccess: (_pat, target) => {
      toast.success(target.type === "revoke" ? "个人访问令牌已撤销。" : "个人访问令牌已恢复。");
      setAction(null);
      void queryClient.invalidateQueries({ queryKey: PAT_LIST_QUERY_KEY });
    },
    onError: (error) => {
      setAction(null);
      toast.error(resolveApiError(error, ERROR_MESSAGES));
    },
  });

  const pats = listQuery.data ?? [];

  const openCreate = () => {
    setEditingName(null);
    setDialogOpen(true);
  };

  const openEdit = (name: string) => {
    setEditingName(name);
    setDialogOpen(true);
  };

  return (
    <ConsoleSection>
      <ConsoleSectionHeader
        title="个人访问令牌"
        description="用于脚本和客户端的长期凭据，可随时撤销。明文只在创建时显示一次。"
        action={
          <div className="flex items-center gap-2">
            <Button variant="ghost" size="sm" onClick={() => void listQuery.refetch()}>
              刷新
            </Button>
            <Button size="sm" onClick={openCreate}>
              <Plus />
              生成令牌
            </Button>
          </div>
        }
      />
      {listQuery.isPending ? (
        <ConsoleBlockSkeleton rows={3} className="grid gap-2 p-4" itemClassName="h-12" />
      ) : listQuery.isError ? (
        <ConsoleState
          variant="error"
          title="无法读取个人访问令牌。"
          description="令牌列表暂时不可用，请重试。"
          onRetry={() => void listQuery.refetch()}
        />
      ) : pats.length === 0 ? (
        <ConsoleState
          variant="empty"
          title="尚未生成个人访问令牌。"
          description="为脚本或客户端生成令牌后，可在这里查看状态、编辑或撤销。"
        />
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>名称</TableHead>
              <TableHead>状态</TableHead>
              <TableHead>过期时间</TableHead>
              <TableHead>最近使用</TableHead>
              <TableHead className="text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {pats.map((pat) => {
              const state = patState(pat);
              return (
                <TableRow key={pat.metadata.name}>
                  <TableCell>
                    <p className="text-sm font-medium">{patDisplayName(pat)}</p>
                    <p className="font-mono text-xs text-muted-foreground">{pat.metadata.name}</p>
                    {pat.spec?.description ? (
                      <p className="mt-1 text-xs text-muted-foreground">{pat.spec.description}</p>
                    ) : null}
                  </TableCell>
                  <TableCell>
                    <Badge
                      variant={
                        state === "ACTIVE"
                          ? "default"
                          : state === "REVOKED"
                            ? "secondary"
                            : "destructive"
                      }
                    >
                      {STATE_LABELS[state]}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-xs text-muted-foreground">
                    {formatUserDateTime(pat.spec?.expiresAt)}
                  </TableCell>
                  <TableCell className="text-xs text-muted-foreground">
                    {formatUserDateTime(pat.spec?.lastUsed)}
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex justify-end gap-1">
                      <Button variant="ghost" size="sm" onClick={() => openEdit(pat.metadata.name)}>
                        <Pencil />
                        编辑
                      </Button>
                      {state === "REVOKED" ? (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() =>
                            setAction({
                              type: "restore",
                              name: pat.metadata.name,
                              label: patDisplayName(pat),
                            })
                          }
                        >
                          <RotateCcw />
                          恢复
                        </Button>
                      ) : state === "ACTIVE" ? (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() =>
                            setAction({
                              type: "revoke",
                              name: pat.metadata.name,
                              label: patDisplayName(pat),
                            })
                          }
                        >
                          <Ban />
                          撤销
                        </Button>
                      ) : null}
                    </div>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      )}
      <PatDialog
        open={dialogOpen}
        patName={editingName}
        onOpenChange={setDialogOpen}
        onCreated={setCreatedPat}
      />
      <Dialog
        open={createdPat !== null}
        onOpenChange={(open) => {
          if (!open) {
            setCreatedPat(null);
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>令牌已生成</DialogTitle>
            <DialogDescription>这是唯一一次显示明文，关闭后无法再次查看。</DialogDescription>
          </DialogHeader>
          {createdPat?.token ? (
            <CopyField
              value={createdPat.token}
              label="令牌明文"
              hint="请立即复制并保存到安全位置。"
            />
          ) : (
            <p className="text-sm text-destructive">响应未返回一次性令牌明文，请检查后端配置。</p>
          )}
          <DialogFooter>
            <Button onClick={() => setCreatedPat(null)}>我已保存</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      <ConfirmDialog
        open={action !== null}
        onOpenChange={(open) => {
          if (!open) {
            setAction(null);
          }
        }}
        title={action?.type === "revoke" ? "撤销个人访问令牌" : "恢复个人访问令牌"}
        desc={
          action
            ? action.type === "revoke"
              ? `确定撤销「${action.label}」？使用该令牌的脚本或客户端会立即失效。`
              : `确定恢复「${action.label}」？恢复后该令牌立即重新生效。`
            : ""
        }
        destructive={action?.type === "revoke"}
        confirmText={action?.type === "revoke" ? "撤销" : "恢复"}
        isLoading={actionMutation.isPending}
        handleConfirm={() => {
          if (action) {
            actionMutation.mutate(action);
          }
        }}
      />
    </ConsoleSection>
  );
}

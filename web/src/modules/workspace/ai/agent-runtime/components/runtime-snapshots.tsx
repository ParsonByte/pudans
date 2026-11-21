"use client";

import { type FormEvent, useId, useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { isAxiosError } from "axios";
import { Camera, CircleAlert, History, Loader2, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";
import { FormField } from "@/components/form-field";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  ConsoleBlockSkeleton,
  ConsoleState,
} from "@/modules/workspace/console/components/console-state";
import { formatFileSize } from "@/modules/workspace/console/storage-policies/lib/model";

import {
  type AiRuntimeSnapshotView,
  aiRuntimeSnapshotsQueryKey,
  createRuntimeSnapshot,
  deleteRuntimeSnapshot,
  fetchRuntime,
  fetchRuntimeSnapshots,
  resolveAgentRuntimeError,
  restoreRuntimeSnapshot,
} from "../lib/api";
import { formatRuntimeDateTime } from "../lib/model";

type RuntimeSnapshotsProps = {
  agent: string;
  running: boolean;
};

type SnapshotConfirm = {
  kind: "restore" | "delete";
  snapshot: AiRuntimeSnapshotView;
};

async function requireRunningRuntime(agent: string): Promise<void> {
  const runtime = await fetchRuntime(agent);
  if (runtime.state !== "RUNNING") {
    throw new Error("沙箱未运行，请先启动运行时。");
  }
}

function resolveSnapshotError(error: unknown): string {
  if (!isAxiosError(error) && error instanceof Error && error.message) {
    return error.message;
  }
  return resolveAgentRuntimeError(error);
}

function formatSnapshotSize(sizeBytes: number): string {
  return sizeBytes > 0 ? formatFileSize(sizeBytes) : "—";
}

export function RuntimeSnapshots({ agent, running }: RuntimeSnapshotsProps) {
  const fieldId = useId();
  const queryClient = useQueryClient();
  const [name, setName] = useState("");
  const [confirm, setConfirm] = useState<SnapshotConfirm | null>(null);

  const snapshotsQuery = useQuery({
    queryKey: aiRuntimeSnapshotsQueryKey(agent),
    queryFn: () => fetchRuntimeSnapshots(agent),
    retry: false,
  });

  const createMutation = useMutation({
    mutationFn: async (snapshotName: string) => {
      await requireRunningRuntime(agent);
      return createRuntimeSnapshot(agent, snapshotName);
    },
    onSuccess: async () => {
      setName("");
      toast.success("快照已创建。");
      await queryClient.invalidateQueries({ queryKey: aiRuntimeSnapshotsQueryKey(agent) });
    },
    onError: (error) => toast.error(resolveSnapshotError(error)),
  });

  const restoreMutation = useMutation({
    mutationFn: async (snapshot: AiRuntimeSnapshotView) => {
      await requireRunningRuntime(agent);
      await restoreRuntimeSnapshot(agent, snapshot.id);
    },
    onSuccess: () => {
      setConfirm(null);
      toast.success("快照已恢复到工作区。");
    },
    onError: (error) => toast.error(resolveSnapshotError(error)),
  });

  const deleteMutation = useMutation({
    mutationFn: (snapshot: AiRuntimeSnapshotView) => deleteRuntimeSnapshot(agent, snapshot.id),
    onSuccess: async () => {
      setConfirm(null);
      toast.success("快照已删除。");
      await queryClient.invalidateQueries({ queryKey: aiRuntimeSnapshotsQueryKey(agent) });
    },
    onError: (error) => toast.error(resolveAgentRuntimeError(error)),
  });

  const busy = createMutation.isPending || restoreMutation.isPending || deleteMutation.isPending;
  const snapshots = snapshotsQuery.data ?? [];

  const submitCreate = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    createMutation.mutate(name.trim());
  };

  return (
    <div className="space-y-4">
      {!running ? (
        <Alert>
          <CircleAlert />
          <AlertTitle>沙箱未运行</AlertTitle>
          <AlertDescription>启动运行时后才能创建或恢复快照，已有快照仍可删除。</AlertDescription>
        </Alert>
      ) : null}

      <form onSubmit={submitCreate} className="flex flex-col gap-2 sm:flex-row sm:items-end">
        <div className="flex-1">
          <FormField
            label="快照名称（可选）"
            htmlFor={`${fieldId}-name`}
            hint="留空时由服务端生成名称。"
          >
            <Input
              id={`${fieldId}-name`}
              value={name}
              onChange={(event) => setName(event.target.value)}
              placeholder="例如：发布前备份"
              disabled={!running || createMutation.isPending}
            />
          </FormField>
        </div>
        <Button type="submit" variant="outline" disabled={!running || createMutation.isPending}>
          {createMutation.isPending ? <Loader2 className="animate-spin" /> : <Camera />}
          创建快照
        </Button>
      </form>

      {snapshotsQuery.isError ? (
        <ConsoleState
          variant="error"
          title="无法加载快照"
          description={resolveAgentRuntimeError(snapshotsQuery.error)}
          onRetry={() => void snapshotsQuery.refetch()}
        />
      ) : snapshotsQuery.isPending ? (
        <ConsoleBlockSkeleton rows={3} className="space-y-2" itemClassName="h-14" />
      ) : snapshots.length === 0 ? (
        <ConsoleState
          variant="empty"
          title="还没有快照"
          description="创建快照会把当前工作区归档保存，之后可随时恢复。"
        />
      ) : (
        <ul className="divide-y border">
          {snapshots.map((snapshot) => (
            <li
              key={snapshot.id}
              className="flex flex-wrap items-center justify-between gap-3 px-3 py-2.5"
            >
              <div className="min-w-0 space-y-0.5">
                <p className="truncate text-sm font-medium">{snapshot.name}</p>
                <p className="text-[11px] text-muted-foreground">
                  #{snapshot.id} · {formatSnapshotSize(snapshot.sizeBytes)} ·{" "}
                  {snapshot.createdBy || "—"} · {formatRuntimeDateTime(snapshot.createdAt)}
                </p>
              </div>
              <div className="flex items-center gap-1.5">
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  disabled={!running || busy}
                  onClick={() => setConfirm({ kind: "restore", snapshot })}
                >
                  <History />
                  恢复
                </Button>
                <Button
                  type="button"
                  size="sm"
                  variant="ghost"
                  className="text-destructive hover:text-destructive"
                  disabled={busy}
                  onClick={() => setConfirm({ kind: "delete", snapshot })}
                >
                  <Trash2 />
                  删除
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}

      {confirm ? (
        <ConfirmDialog
          open
          onOpenChange={(open) => !open && setConfirm(null)}
          title={confirm.kind === "restore" ? "恢复快照" : "删除快照"}
          desc={
            confirm.kind === "restore"
              ? `将把快照「${confirm.snapshot.name}」解压到当前工作区并覆盖同名文件。`
              : `将永久删除快照「${confirm.snapshot.name}」，删除后无法恢复。`
          }
          confirmText={confirm.kind === "restore" ? "恢复" : "删除"}
          destructive={confirm.kind === "delete"}
          isLoading={
            confirm.kind === "restore" ? restoreMutation.isPending : deleteMutation.isPending
          }
          handleConfirm={() =>
            confirm.kind === "restore"
              ? restoreMutation.mutate(confirm.snapshot)
              : deleteMutation.mutate(confirm.snapshot)
          }
        />
      ) : null}
    </div>
  );
}

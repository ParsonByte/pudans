import { useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, Pencil, Save, X } from "lucide-react";
import { toast } from "sonner";

import { DetailDialog } from "@/components/detail-dialog";
import { InfoRow } from "@/components/info-row";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { formatBeijingDateTime } from "@/lib/date";

import {
  type StoragePolicyItem,
  type StoragePolicyPatchOperation,
  type StoragePolicyTypeDescriptor,
  fetchStoragePolicy,
  patchStoragePolicy,
  resolveStorageApiError,
} from "../lib/api";
import {
  formatFileSize,
  formatFileTypes,
  isLocal,
  policyEndpoint,
  storageTypeDescription,
  storageTypeLabel,
} from "../lib/model";

const STORAGE_POLICY_QUERY_KEY = ["storage-policy"] as const;

type PolicyDetailDialogProps = {
  policy: StoragePolicyItem;
  descriptor?: StoragePolicyTypeDescriptor;
  canUpdate: boolean;
  onClose: () => void;
};

export function PolicyDetailDialog({
  policy,
  descriptor,
  canUpdate,
  onClose,
}: PolicyDetailDialogProps) {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [displayName, setDisplayName] = useState(policy.spec.displayName ?? "");
  const [description, setDescription] = useState(policy.spec.description ?? "");

  const policyQuery = useQuery({
    queryKey: [...STORAGE_POLICY_QUERY_KEY, policy.metadata.name],
    queryFn: () => fetchStoragePolicy(policy.metadata.name),
    initialData: policy,
    retry: false,
  });
  const current = policyQuery.data;
  const descriptors = descriptor ? [descriptor] : [];
  const ready = current.status?.ready !== false;
  const credentialKeys = Object.entries(current.status?.credentialConfigured ?? {})
    .filter(([, configured]) => configured)
    .map(([key]) => key);

  const startEditing = () => {
    setDisplayName(current.spec.displayName ?? "");
    setDescription(current.spec.description ?? "");
    setEditing(true);
  };

  const saveMutation = useMutation({
    mutationFn: () => {
      const operations: StoragePolicyPatchOperation[] = [
        { op: "add", path: "/spec/displayName", value: displayName.trim() },
      ];
      if (description.trim()) {
        operations.push({ op: "add", path: "/spec/description", value: description.trim() });
      } else if (current.spec.description) {
        operations.push({ op: "remove", path: "/spec/description" });
      }
      return patchStoragePolicy(current.metadata.name, operations);
    },
    onSuccess: async (updated) => {
      queryClient.setQueryData([...STORAGE_POLICY_QUERY_KEY, updated.metadata.name], updated);
      await queryClient.invalidateQueries({ queryKey: ["storage-policies"] });
      setEditing(false);
      toast.success("存储策略基本信息已保存。");
    },
    onError: (error) => toast.error(resolveStorageApiError(error)),
  });

  const save = () => {
    if (!displayName.trim()) {
      toast.error("请输入策略名称。");
      return;
    }
    saveMutation.mutate();
  };

  return (
    <DetailDialog
      open
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
      title={current.spec.displayName || current.metadata.name}
      description={`${storageTypeLabel(current.spec.type, descriptors)} · ${current.metadata.name}`}
      size="3xl"
      footer={
        editing ? (
          <>
            <Button
              type="button"
              variant="outline"
              disabled={saveMutation.isPending}
              onClick={() => setEditing(false)}
            >
              <X />
              取消
            </Button>
            <Button type="button" disabled={saveMutation.isPending} onClick={save}>
              {saveMutation.isPending ? <Loader2 className="animate-spin" /> : <Save />}
              保存
            </Button>
          </>
        ) : (
          <>
            <Button type="button" variant="outline" onClick={onClose}>
              关闭
            </Button>
            {canUpdate ? (
              <Button type="button" onClick={startEditing}>
                <Pencil />
                编辑基本信息
              </Button>
            ) : null}
          </>
        )
      }
    >
      <div className="space-y-5">
        {editing ? (
          <section className="space-y-3 border bg-card/40 p-4">
            <label className="block space-y-1 text-sm">
              <span className="text-xs text-muted-foreground">名称</span>
              <Input
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
                placeholder="存储策略名称"
              />
            </label>
            <label className="block space-y-1 text-sm">
              <span className="text-xs text-muted-foreground">描述</span>
              <Textarea
                value={description}
                onChange={(event) => setDescription(event.target.value)}
                rows={2}
                placeholder="说明该存储的用途。"
              />
            </label>
            <p className="text-xs text-muted-foreground">
              凭据、桶配置与上传规则请使用「编辑」对话框修改。
            </p>
          </section>
        ) : null}

        <div className="grid gap-3 sm:grid-cols-2">
          <InfoRow label="类型" value={storageTypeLabel(current.spec.type, descriptors)} />
          <InfoRow
            label="状态"
            value={
              <Badge variant={ready ? "secondary" : "outline"}>{ready ? "就绪" : "待就绪"}</Badge>
            }
          />
          <InfoRow
            label="默认策略"
            value={current.spec.defaultPolicy ? <Badge>默认</Badge> : "否"}
          />
          <InfoRow label="文件上限" value={formatFileSize(current.spec.maxSize)} />
          <InfoRow label="EndPoint" value={policyEndpoint(current) || "—"} />
          <InfoRow
            label={isLocal(current.spec.type) ? "存储位置" : "Bucket"}
            value={isLocal(current.spec.type) ? "本机存储" : current.spec.bucketName || "—"}
          />
          <InfoRow
            label="下载中转"
            value={current.spec.proxyDownload === false ? "关闭" : "开启"}
          />
          <InfoRow label="私有 Bucket" value={current.spec.privateBucket ? "是" : "否"} />
          <InfoRow label="目录规则" value={current.spec.dirNameRule || "—"} />
          <InfoRow label="文件命名规则" value={current.spec.fileNameRule || "—"} />
          <InfoRow label="文件类型" value={formatFileTypes(current.spec.fileTypes) || "不限制"} />
          <InfoRow label="最近测试" value={formatBeijingDateTime(current.status?.lastTestedAt)} />
        </div>

        <section className="space-y-2">
          <h3 className="text-sm font-semibold">说明</h3>
          <p className="text-sm text-muted-foreground">
            {current.spec.description || storageTypeDescription(current.spec.type, descriptors)}
          </p>
        </section>

        <section className="space-y-2">
          <h3 className="text-sm font-semibold">凭据</h3>
          {credentialKeys.length > 0 ? (
            <div className="flex flex-wrap gap-2">
              {credentialKeys.map((key) => (
                <Badge key={key} variant="secondary">
                  {key} 已配置
                </Badge>
              ))}
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">未记录已配置的凭据字段。</p>
          )}
          {current.status?.message ? (
            <p className="text-xs text-muted-foreground">{current.status.message}</p>
          ) : null}
        </section>

        {policyQuery.isError ? (
          <p className="text-xs text-destructive">{resolveStorageApiError(policyQuery.error)}</p>
        ) : null}
      </div>
    </DetailDialog>
  );
}

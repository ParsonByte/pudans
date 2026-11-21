import { useState } from "react";

import { type UserConnection, apiClient } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Fingerprint, Loader2, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { resolveApiError } from "@/lib/api-error";
import { createPasskey, serializeRegistration } from "@/lib/webauthn";
import {
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";
import {
  ConsoleBlockSkeleton,
  ConsoleState,
} from "@/modules/workspace/console/components/console-state";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";

const PASSKEYS_QUERY_KEY = ["account-passkeys"];

const ERROR_MESSAGES = {
  notFound: "通行密钥不存在。",
  conflict: "操作失败，请稍后重试。",
};

export function PasskeysSection() {
  const queryClient = useQueryClient();
  const passkeysQuery = useQuery({
    queryKey: PASSKEYS_QUERY_KEY,
    queryFn: async () => (await apiClient.passkey.listPasskeys()).data,
  });
  const [pendingDelete, setPendingDelete] = useState<UserConnection | null>(null);

  const refresh = () => queryClient.invalidateQueries({ queryKey: PASSKEYS_QUERY_KEY });

  const addMutation = useMutation({
    mutationFn: async () => {
      if (!window.PublicKeyCredential) {
        throw new Error("当前浏览器不支持通行密钥。");
      }
      const options = (await apiClient.passkey.passkeyRegistrationOptions()).data.publicKey;
      if (!options) {
        throw new Error("缺少通行密钥注册参数。");
      }
      const credential = await createPasskey(options as Record<string, unknown>);
      await apiClient.passkey.passkeyRegistrationVerify({
        body: serializeRegistration(credential),
      });
    },
    onSuccess: () => {
      toast.success("通行密钥已添加。");
      void refresh();
    },
    onError: (error) => {
      if (error instanceof DOMException && error.name === "NotAllowedError") {
        toast.info("已取消通行密钥注册。");
        return;
      }
      toast.error(error instanceof Error ? error.message : "通行密钥添加失败。");
    },
  });

  const deleteMutation = useMutation({
    mutationFn: async (name: string) => {
      await apiClient.userConnection.unbindConnection({ name });
    },
    onSuccess: () => {
      toast.success("通行密钥已移除。");
      setPendingDelete(null);
      void refresh();
    },
    onError: (error) => {
      setPendingDelete(null);
      toast.error(resolveApiError(error, ERROR_MESSAGES));
    },
  });

  const passkeys = passkeysQuery.data ?? [];

  return (
    <ConsoleSection>
      <ConsoleSectionHeader
        title="通行密钥"
        description="使用设备上的指纹、面部识别或安全密钥免密码登录。"
        action={
          <Button
            variant="outline"
            size="sm"
            onClick={() => addMutation.mutate()}
            disabled={addMutation.isPending}
          >
            {addMutation.isPending ? <Loader2 className="animate-spin" /> : <Fingerprint />}
            添加通行密钥
          </Button>
        }
      />
      {passkeysQuery.isPending ? (
        <ConsoleBlockSkeleton rows={3} className="grid gap-2 p-4" itemClassName="h-12" />
      ) : passkeysQuery.isError ? (
        <ConsoleState
          variant="error"
          title="无法读取通行密钥。"
          description="通行密钥列表暂时不可用，请重试。"
          onRetry={() => void passkeysQuery.refetch()}
        />
      ) : passkeys.length === 0 ? (
        <ConsoleState
          variant="empty"
          title="尚未添加通行密钥。"
          description="添加后，可以使用设备上的指纹、面部识别或安全密钥登录。"
        />
      ) : (
        <div className="divide-y">
          {passkeys.map((passkey) => (
            <div key={passkey.metadata.name} className="flex items-center gap-3 px-5 py-3">
              <span className="flex size-9 shrink-0 items-center justify-center bg-muted">
                <Fingerprint className="size-4" />
              </span>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium">{passkey.spec.displayName ?? "通行密钥"}</p>
                <p className="truncate font-mono text-xs text-muted-foreground">
                  {passkey.spec.providerUserId}
                </p>
              </div>
              <Badge variant="secondary">绑定于 {formatUserDateTime(passkey.spec.updatedAt)}</Badge>
              <Button variant="ghost" size="sm" onClick={() => setPendingDelete(passkey)}>
                <Trash2 />
                移除
              </Button>
            </div>
          ))}
        </div>
      )}
      <ConfirmDialog
        open={pendingDelete !== null}
        onOpenChange={(open) => {
          if (!open) {
            setPendingDelete(null);
          }
        }}
        title="移除通行密钥"
        desc={
          pendingDelete
            ? `确定移除「${pendingDelete.spec.displayName ?? pendingDelete.spec.providerUserId}」？移除后该设备将无法使用此通行密钥登录。`
            : ""
        }
        destructive
        confirmText="移除"
        isLoading={deleteMutation.isPending}
        handleConfirm={() => {
          if (pendingDelete) {
            deleteMutation.mutate(pendingDelete.metadata.name);
          }
        }}
      />
    </ConsoleSection>
  );
}

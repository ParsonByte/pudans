import { useState } from "react";

import { type UserDevice, apiClient } from "@pudans-dev/api-client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, MonitorSmartphone, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { resolveApiError } from "@/lib/api-error";
import {
  ConsoleEmpty,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";

const DEVICES_QUERY_KEY = ["account-devices"];

const ERROR_MESSAGES = {
  notFound: "设备不存在。",
  conflict: "操作失败，请稍后重试。",
};

export function DevicesSection() {
  const queryClient = useQueryClient();
  const devicesQuery = useQuery({
    queryKey: DEVICES_QUERY_KEY,
    queryFn: async () => (await apiClient.device.listDevices({})).data,
  });
  const [pendingRevoke, setPendingRevoke] = useState<UserDevice | null>(null);

  const refresh = () => queryClient.invalidateQueries({ queryKey: DEVICES_QUERY_KEY });

  const revokeMutation = useMutation({
    mutationFn: async (deviceId: string) => {
      await apiClient.device.revokeDevice({ deviceId });
    },
    onSuccess: () => {
      toast.success("设备已退出登录。");
      setPendingRevoke(null);
      void refresh();
    },
    onError: (error) => {
      setPendingRevoke(null);
      toast.error(resolveApiError(error, ERROR_MESSAGES));
    },
  });

  const devices = devicesQuery.data ?? [];

  return (
    <ConsoleSection>
      <ConsoleSectionHeader
        title="登录设备"
        description="查看当前账号的登录会话，可让其他设备退出登录。"
        action={
          <Button variant="ghost" size="sm" onClick={() => void refresh()}>
            刷新
          </Button>
        }
      />
      {devicesQuery.isPending ? (
        <div className="flex min-h-24 items-center justify-center">
          <Loader2 className="size-4 animate-spin text-muted-foreground" />
        </div>
      ) : devicesQuery.isError ? (
        <ConsoleEmpty>无法读取登录设备。</ConsoleEmpty>
      ) : devices.length === 0 ? (
        <ConsoleEmpty>暂无登录设备。</ConsoleEmpty>
      ) : (
        <div className="divide-y">
          {devices.map((item) => {
            const device = item.device;
            const browser = device.status?.browser;
            const os = device.status?.os;
            return (
              <div key={device.metadata.name} className="flex items-center gap-3 px-5 py-3">
                <span className="flex size-9 shrink-0 items-center justify-center bg-muted">
                  <MonitorSmartphone className="size-4" />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium">
                    {device.spec.userAgent || "未知设备"}
                  </p>
                  <p className="truncate text-xs text-muted-foreground">
                    {[os, browser, device.spec.ipAddress].filter(Boolean).join(" · ") || "—"}
                  </p>
                </div>
                <span className="shrink-0 text-xs text-muted-foreground">
                  {formatUserDateTime(
                    device.spec.lastAccessedTime ?? device.spec.lastAuthenticatedTime,
                  )}
                </span>
                {item.currentDevice ? <Badge>当前设备</Badge> : null}
                {!item.currentDevice && item.active ? (
                  <Badge variant="secondary">活跃</Badge>
                ) : null}
                <Button
                  variant="ghost"
                  size="sm"
                  disabled={item.currentDevice}
                  onClick={() => setPendingRevoke(item)}
                >
                  <Trash2 />
                  退出
                </Button>
              </div>
            );
          })}
        </div>
      )}
      <ConfirmDialog
        open={pendingRevoke !== null}
        onOpenChange={(open) => {
          if (!open) {
            setPendingRevoke(null);
          }
        }}
        title="退出设备"
        desc="确定让该设备退出登录？该设备上的会话会立即失效。"
        destructive
        confirmText="退出"
        isLoading={revokeMutation.isPending}
        handleConfirm={() => {
          if (pendingRevoke) {
            revokeMutation.mutate(pendingRevoke.device.metadata.name);
          }
        }}
      />
    </ConsoleSection>
  );
}
